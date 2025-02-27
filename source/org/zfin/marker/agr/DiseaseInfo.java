package org.zfin.marker.agr;

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.zfin.expression.ExperimentCondition;
import org.zfin.feature.Feature;
import org.zfin.infrastructure.ActiveData;
import org.zfin.marker.Marker;
import org.zfin.mutant.*;
import org.zfin.ontology.GenericTerm;
import org.zfin.ontology.datatransfer.AbstractScriptWrapper;
import org.zfin.publication.Publication;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static org.zfin.repository.RepositoryFactory.getMutantRepository;
import static org.zfin.repository.RepositoryFactory.getOntologyRepository;

public class DiseaseInfo extends AbstractScriptWrapper {

    private int numfOfRecords = 0;

    public DiseaseInfo(int number) {
        numfOfRecords = number;
    }


    public static void main(String[] args) throws IOException {
        int number = 0;
        if (args.length > 0) {
            number = Integer.parseInt(args[0]);
        }
        DiseaseInfo diseaseInfo = new DiseaseInfo(number);
        diseaseInfo.init();
        System.exit(0);
    }

    private void init() throws IOException {
        initAll();
        AllDiseaseDTO allDiseaseDTO = getDiseaseInfo(numfOfRecords);
        ObjectMapper mapper = new ObjectMapper();
        ObjectWriter writer = mapper.writer(new DefaultPrettyPrinter());
        String jsonInString = writer.writeValueAsString(allDiseaseDTO);
        try (PrintStream out = new PrintStream(new FileOutputStream("ZFIN_1.0.1.4_disease.daf.json"))) {
            out.print(jsonInString);
        }
    }

    public AllDiseaseDTO getDiseaseInfo(int numberOrRecords) {
        List<DiseaseDTO> diseaseDTOList = new ArrayList<>();


        // get all genes from mutant_fast_search table and list their disease info
        List<GeneGenotypeExperiment> geneGenotypeExperiments = getMutantRepository().getGeneDiseaseAnnotationModels(numberOrRecords);

        // group by gene records
        Map<Marker, Set<FishExperiment>> diseaseModelMap =
                geneGenotypeExperiments.stream().collect(
                        Collectors.groupingBy(GeneGenotypeExperiment::getGene,
                                Collectors.mapping(GeneGenotypeExperiment::getFishExperiment, Collectors.toSet())));

        // loop over each gene
        diseaseModelMap.forEach((gene, fishExperimentSet) -> {
            // loop over each FishExperiment

            fishExperimentSet.forEach((FishExperiment fishExperiment) -> {
                Genotype genotype = fishExperiment.getFish().getGenotype();

                Fish fish = fishExperiment.getFish();
                // group the diseaseAnnotation by disease
                // so publications and evidence codes are grouped together
                Map<GenericTerm, Set<DiseaseAnnotation>> termMap = fishExperiment.getDiseaseAnnotationModels()
                        .stream()
                        .collect(
                                Collectors.groupingBy(diseaseAnnotationModel -> diseaseAnnotationModel.getDiseaseAnnotation().getDisease(),
                                        Collectors.mapping(DiseaseAnnotationModel::getDiseaseAnnotation, Collectors.toSet())
                                )
                        );
                // loop over each disease
                termMap.forEach((disease, diseaseAnnotations) -> {
                    // group disease annotations into pubs and their corresponding list of evidence codes
                    Map<Publication, List<String>> evidenceMap = diseaseAnnotations
                            .stream()
                            .collect(
                                    Collectors.groupingBy(DiseaseAnnotation::getPublication,
                                            Collectors.mapping(annotation -> annotation.getEvidenceCode().getOboID(), toList())
                                    )
                            );
                    Map<Publication, List<String>> publicationDateMap = diseaseAnnotations
                            .stream()
                            .collect(
                                    Collectors.groupingBy(DiseaseAnnotation::getPublication,
                                            Collectors.mapping(DiseaseAnnotation::getZdbID, toList())
                                    )
                            );

                    // Hack: get the date stamp from the ZDB-DAT ID.
                    // Use the earliest one we have per pub
                    Map<Publication, GregorianCalendar> map = new HashMap<>();
                    publicationDateMap.forEach((publication, ids) -> {
                        ids.sort(Comparator.naturalOrder());
                        GregorianCalendar date = ActiveData.getDateFromId(ids.get(0));
                        map.put(publication, date);
                    });
                    // loop over each publication: final loop as each publication should generate a individual record in the file.
                    evidenceMap.forEach((publication, evidenceSet) -> {
                        // Use wildtype fish with STR
                        // treat as purely implicated by a gene
                        if (genotype.isWildtype()) {
                            DiseaseDTO strDiseaseDto = getBaseDiseaseDTO(gene.getZdbID(), gene.getAbbreviation(), disease);
                            strDiseaseDto.setDateAssigned(map.get(publication));
                            RelationshipDTO relationship = new RelationshipDTO(RelationshipDTO.IS_IMPLICATED_IN, RelationshipDTO.GENE);
                            strDiseaseDto.setObjectRelation(relationship);
                            List<String> geneticEntityIds = new ArrayList<>();
                            geneticEntityIds.add("ZFIN:" + fish.getZdbID());
                            strDiseaseDto.setPrimaryGeneticEntityIDs(geneticEntityIds);
                            // evidence
                            strDiseaseDto.setEvidence(getEvidenceDTO(publication, evidenceSet));
                            strDiseaseDto.setPrimaryGeneticEntityIDs(geneticEntityIds);
                            diseaseDTOList.add(strDiseaseDto);

                            DiseaseDTO fishDiseaseDto = getBaseDiseaseDTO(fish.getZdbID(), fish.getName(), disease);
                            RelationshipDTO fishRelationship = new RelationshipDTO(RelationshipDTO.IS_MODEL_OF, RelationshipDTO.FISH);
                            fishDiseaseDto.setObjectRelation(fishRelationship);
                            fishDiseaseDto.setEvidence(getEvidenceDTO(publication, evidenceSet));
                            ConditionRelationDTO condition = populateExperimentConditions(fishExperiment);
                            List<ConditionRelationDTO> conditions = new ArrayList<>();
                            conditions.add(condition);
                            fishDiseaseDto.setConditionRelations(conditions);
                            diseaseDTOList.add(fishDiseaseDto);

                        } else {
                            DiseaseDTO GeneDiseaseDto = getBaseDiseaseDTO(gene.getZdbID(), gene.getAbbreviation(), disease);
                            GeneDiseaseDto.setDateAssigned(map.get(publication));

                            RelationshipDTO geneRelationship = new RelationshipDTO(RelationshipDTO.IS_IMPLICATED_IN, RelationshipDTO.GENE);

                            GeneDiseaseDto.setObjectRelation(geneRelationship);

                            genotype.getGenotypeFeatures().forEach(genotypeFeature -> {
                                Feature feature = genotypeFeature.getFeature();
                                // is it a single-allelic feature use it
                                // otherwise discard record
                                if (fish.getFishFunctionalAffectedGeneCount() == 1) {
                                    if (feature.isSingleAlleleOfMarker(gene)) {
                                        DiseaseDTO FeatureDiseaseDto = getBaseDiseaseDTO(feature.getZdbID(), feature.getAbbreviation(), disease);
                                        FeatureDiseaseDto.setDateAssigned(map.get(publication));
                                        RelationshipDTO alleleRelationship = new RelationshipDTO(RelationshipDTO.IS_IMPLICATED_IN, RelationshipDTO.ALELLE);
                                        List<String> geneticEntityIds = new ArrayList<>();
                                        geneticEntityIds.add("ZFIN:" + fish.getZdbID());
                                        FeatureDiseaseDto.setPrimaryGeneticEntityIDs(geneticEntityIds);
                                        FeatureDiseaseDto.setObjectRelation(alleleRelationship);
                                        FeatureDiseaseDto.setEvidence(getEvidenceDTO(publication, evidenceSet));
//                                        ConditionRelationDTO condition = populateExperimentConditions(fishExperiment, FeatureDiseaseDto);
//                                        List<ConditionRelationDTO> conditions = new ArrayList<>();
//                                        conditions.add(condition);
//                                        FeatureDiseaseDto.setConditionRelations(conditions);
                                        diseaseDTOList.add(FeatureDiseaseDto);

                                        DiseaseDTO geneDiseaseDto = getBaseDiseaseDTO(gene.getZdbID(), gene.getAbbreviation(), disease);
                                        geneDiseaseDto.setDateAssigned(map.get(publication));
                                        RelationshipDTO relationship = new RelationshipDTO(RelationshipDTO.IS_IMPLICATED_IN, RelationshipDTO.GENE);
                                        List<String> geneGeneticEntityIds = new ArrayList<>();
                                        geneGeneticEntityIds.add("ZFIN:" + fish.getZdbID());
                                        geneDiseaseDto.setPrimaryGeneticEntityIDs(geneGeneticEntityIds);
                                        geneDiseaseDto.setObjectRelation(relationship);
                                        geneDiseaseDto.setEvidence(getEvidenceDTO(publication, evidenceSet));
//                                        ConditionRelationDTO condition2 = populateExperimentConditions(fishExperiment, geneDiseaseDto);
//                                        List<ConditionRelationDTO> conditions2 = new ArrayList<>();
//                                        conditions.add(condition2);
//                                        geneDiseaseDto.setConditionRelations(conditions2);
                                        diseaseDTOList.add(geneDiseaseDto);

                                    } else {
                                        DiseaseDTO FeatureDiseaseDto = getBaseDiseaseDTO(gene.getZdbID(), gene.getAbbreviation(), disease);
                                        FeatureDiseaseDto.setDateAssigned(map.get(publication));
                                        RelationshipDTO relationship = new RelationshipDTO(RelationshipDTO.IS_IMPLICATED_IN, RelationshipDTO.GENE);
                                        List<String> geneticEntityIds = new ArrayList<>();
                                        geneticEntityIds.add("ZFIN:" + fish.getZdbID());
                                        FeatureDiseaseDto.setPrimaryGeneticEntityIDs(geneticEntityIds);
                                        FeatureDiseaseDto.setObjectRelation(relationship);
                                        FeatureDiseaseDto.setEvidence(getEvidenceDTO(publication, evidenceSet));
//                                        populateExperimentConditions(fishExperiment, FeatureDiseaseDto);
//                                        ConditionRelationDTO condition = populateExperimentConditions(fishExperiment, FeatureDiseaseDto);
//                                        List<ConditionRelationDTO> conditions = new ArrayList<>();
//                                        conditions.add(condition);
//                                        FeatureDiseaseDto.setConditionRelations(conditions);
                                        diseaseDTOList.add(FeatureDiseaseDto);

                                    }
                                }
                            });
                            DiseaseDTO fishDiseaseDto = getBaseDiseaseDTO(fish.getZdbID(), fish.getName(), disease);
                            fishDiseaseDto.setDateAssigned(map.get(publication));
                            RelationshipDTO fishRelationship = new RelationshipDTO(RelationshipDTO.IS_MODEL_OF, RelationshipDTO.FISH);
                            fishDiseaseDto.setObjectRelation(fishRelationship);
                            fishDiseaseDto.setEvidence(getEvidenceDTO(publication, evidenceSet));
                            ConditionRelationDTO condition = populateExperimentConditions(fishExperiment);
                            List<ConditionRelationDTO> conditions = new ArrayList<>();
                            conditions.add(condition);
                            fishDiseaseDto.setConditionRelations(conditions);
                            diseaseDTOList.add(fishDiseaseDto);

                        }

                    });
                });

            });
        });
//
//        // get all genes from mutant_fast_search table and list their disease info
        List<DiseaseAnnotationModel> damos = getMutantRepository().getDiseaseAnnotationModelsNoStd(numfOfRecords);
        for (DiseaseAnnotationModel damo : damos) {
            Fish fish = damo.getFishExperiment().getFish();
            DiseaseAnnotation disease = damo.getDiseaseAnnotation();
            DiseaseDTO fishDiseaseDto2 = getBaseDiseaseDTO(fish.getZdbID(), fish.getName(), disease.getDisease());
            RelationshipDTO fishRelationship = new RelationshipDTO(RelationshipDTO.IS_MODEL_OF, RelationshipDTO.FISH);
            fishDiseaseDto2.setObjectRelation(fishRelationship);
            List<String> evidenceList = List.of(damo.getDiseaseAnnotation().getEvidenceCode().getOboID());
            fishDiseaseDto2.setEvidence(getEvidenceDTO(damo.getDiseaseAnnotation().getPublication(), evidenceList));


            ConditionRelationDTO relation = new ConditionRelationDTO();
            List<ExperimentCondition> allConditions = getMutantRepository().getExperimentConditions(damo.getFishExperiment().getExperiment());
            relation.setConditionRelationType("has_condition");
            List<ExperimentConditionDTO> expconds2 = new ArrayList<>();
            allConditions = allConditions.stream().distinct().collect(toList());
            for (ExperimentCondition conditionz : allConditions) {

                ExperimentConditionDTO expconda = new ExperimentConditionDTO();
                if (conditionz.getAoTerm() != null) {
                    expconda.setAnatomicalOntologyId(conditionz.getAoTerm().getOboID());
                }
                if (conditionz.getChebiTerm() != null) {
                    expconda.setChemicalOntologyId(conditionz.getChebiTerm().getOboID());
                }
                if (conditionz.getGoCCTerm() != null) {
                    expconda.setGeneOntologyId(conditionz.getGoCCTerm().getOboID());
                }
                if (conditionz.getTaxaonymTerm() != null) {
                    expconda.setNcbiTaxonId(conditionz.getTaxaonymTerm().getOboID());
                }
                populateConditionClassId(expconda, conditionz);
                expconda.setConditionStatement(conditionz.getDisplayName());
                expconds2.add(expconda);
            }
            relation.setConditions(expconds2);

            List<ConditionRelationDTO> conditions = new ArrayList<>();
            conditions.add(relation);
            fishDiseaseDto2.setConditionRelations(conditions);
            diseaseDTOList.add(fishDiseaseDto2);
        }

        AllDiseaseDTO allDiseaseDTO = new AllDiseaseDTO();
        String dataProvider = "ZFIN";
        List<String> pages = new ArrayList<>();
        pages.add("homepage");
        DataProviderDTO dp = new DataProviderDTO("curated", new CrossReferenceDTO(dataProvider, dataProvider, pages));
        MetaDataDTO meta = new MetaDataDTO(new DataProviderDTO("curated", new CrossReferenceDTO(dataProvider, dataProvider, pages)));
        allDiseaseDTO.setMetaData(meta);
        allDiseaseDTO.setDiseaseList(diseaseDTOList);
        return allDiseaseDTO;
    }

    public DiseaseDTO getBaseDiseaseDTO(String zdbID, String abbreviation, GenericTerm disease) {
        DiseaseDTO strDiseaseDto = new DiseaseDTO();
        strDiseaseDto.setObjectId("ZFIN:" + zdbID);
        strDiseaseDto.setObjectName(abbreviation);
        List<String> pages = new ArrayList<>();
        pages.add("disease");
        List<DataProviderDTO> dpList = new ArrayList<>();
        dpList.add(new DataProviderDTO("curated", new CrossReferenceDTO("ZFIN", zdbID, pages)));
        strDiseaseDto.setDataProvider(dpList);
        strDiseaseDto.setDoid(disease.getOboID());
        return strDiseaseDto;
    }

    public ConditionRelationDTO populateExperimentConditions(FishExperiment fishExperiment) {
        ConditionRelationDTO relation = new ConditionRelationDTO();
        if (fishExperiment.getExperiment() != null) {
            List<ExperimentCondition> allConditions = getMutantRepository().getExperimentConditions(fishExperiment.getExperiment());
            relation.setConditionRelationType("has_condition");
            List<ExperimentConditionDTO> expconds = new ArrayList<>();
            for (ExperimentCondition condition : allConditions) {
                ExperimentConditionDTO expcond = new ExperimentConditionDTO();
                String conditionStatement = condition.getZecoTerm().getTermName();
                if (condition.getAoTerm() != null) {
                    conditionStatement = conditionStatement + " " + condition.getAoTerm().getTermName();
                    expcond.setAnatomicalOntologyId(condition.getAoTerm().getOboID());
                }
                if (condition.getChebiTerm() != null) {
                    expcond.setChemicalOntologyId(condition.getChebiTerm().getOboID());
                    conditionStatement = conditionStatement + " " + condition.getChebiTerm().getTermName();
                }
                if (condition.getGoCCTerm() != null) {
                    expcond.setGeneOntologyId(condition.getGoCCTerm().getOboID());
                    conditionStatement = conditionStatement + " " + condition.getGoCCTerm().getTermName();
                }
                if (condition.getTaxaonymTerm() != null) {
                    expcond.setNcbiTaxonId(condition.getTaxaonymTerm().getOboID());
                    conditionStatement = conditionStatement + " " + condition.getTaxaonymTerm().getTermName();
                }
                populateConditionClassId(expcond, condition);
                expcond.setConditionStatement(conditionStatement);
                expconds.add(expcond);
            }
            relation.setConditions(expconds);

        }
        return relation;
    }

    // ToDo: This list should be a slim in ZECO to identify those high-level terms.
    private static final List<GenericTerm> highLevelConditionTerms = new ArrayList<>(18);

    static {
        highLevelConditionTerms.add(new GenericTerm("ZDB-TERM-160831-7", "ZECO:0000105"));
        highLevelConditionTerms.add(new GenericTerm("ZDB-TERM-160831-13", "ZECO:0000111"));
        highLevelConditionTerms.add(new GenericTerm("ZDB-TERM-160831-14", "ZECO:0000112"));
        highLevelConditionTerms.add(new GenericTerm("ZDB-TERM-160831-15", "ZECO:0000113"));
        highLevelConditionTerms.add(new GenericTerm("ZDB-TERM-160831-33", "ZECO:0000131"));
        highLevelConditionTerms.add(new GenericTerm("ZDB-TERM-160831-42", "ZECO:0000140"));
        highLevelConditionTerms.add(new GenericTerm("ZDB-TERM-160831-45", "ZECO:0000143"));
        highLevelConditionTerms.add(new GenericTerm("ZDB-TERM-160831-48", "ZECO:0000146"));
        highLevelConditionTerms.add(new GenericTerm("ZDB-TERM-160831-56", "ZECO:0000154"));
        highLevelConditionTerms.add(new GenericTerm("ZDB-TERM-160831-62", "ZECO:0000160"));
        highLevelConditionTerms.add(new GenericTerm("ZDB-TERM-160831-82", "ZECO:0000182"));
        highLevelConditionTerms.add(new GenericTerm("ZDB-TERM-160831-108", "ZECO:0000208"));
        highLevelConditionTerms.add(new GenericTerm("ZDB-TERM-160831-122", "ZECO:0000222"));
        highLevelConditionTerms.add(new GenericTerm("ZDB-TERM-160831-129", "ZECO:0000229"));
        highLevelConditionTerms.add(new GenericTerm("ZDB-TERM-171108-6", "ZECO:0000252"));
        highLevelConditionTerms.add(new GenericTerm("ZDB-TERM-160831-3", "ZECO:0000101"));
        highLevelConditionTerms.add(new GenericTerm("ZDB-TERM-160831-5", "ZECO:0000103"));
        // make sure it's the last entry as it is a root term.
        highLevelConditionTerms.add(new GenericTerm("ZDB-TERM-160831-6", "ZECO:0000104"));
    }

    private void populateConditionClassId(ExperimentConditionDTO expcond, ExperimentCondition condition) {
        String oboID = condition.getZecoTerm().getOboID();
        if (highLevelConditionTerms.stream().map(GenericTerm::getOboID).collect(toList()).contains(oboID)) {
            expcond.setConditionClassId(oboID);
        } else {
            Optional<GenericTerm> highLevelterm = highLevelConditionTerms.stream().filter(parentTerm -> getOntologyRepository().isParentChildRelationshipExist(parentTerm, condition.getZecoTerm()))
                    .findFirst();
            if (highLevelterm.isPresent()) {
                expcond.setConditionClassId(highLevelterm.get().getOboID());
                expcond.setConditionId(oboID);
            }
        }
    }

    public EvidenceDTO getEvidenceDTO(Publication publication, List<String> evidences) {
        PublicationAgrDTO fixedPub = new PublicationAgrDTO();
        List<String> pubPages = new ArrayList<>();
        pubPages.add("reference");
        CrossReferenceDTO pubXref = new CrossReferenceDTO("ZFIN", publication.getZdbID(), pubPages);
        if (publication.getAccessionNumber() != null) {
            fixedPub.setPublicationId("PMID:" + publication.getAccessionNumber());
            fixedPub.setCrossReference(pubXref);
        } else {
            fixedPub.setPublicationId("ZFIN:" + publication.getZdbID());
        }

        EvidenceDTO evDto = new EvidenceDTO(fixedPub);
        evDto.setEvidenceCodes(evidences);
        return evDto;
    }

    class Item {

        private String name;
        private int qty;
        private BigDecimal price;

        public Item(String name, int qty, BigDecimal price) {
            this.name = name;
            this.qty = qty;
            this.price = price;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getQty() {
            return qty;
        }

        public void setQty(int qty) {
            this.qty = qty;
        }

        public BigDecimal getPrice() {
            return price;
        }

        public void setPrice(BigDecimal price) {
            this.price = price;
        }

        //constructors, getter/setters
    }
}
//test