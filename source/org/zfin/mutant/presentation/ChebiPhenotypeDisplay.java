package org.zfin.mutant.presentation;

import com.fasterxml.jackson.annotation.JsonView;
import lombok.Getter;
import lombok.Setter;
import org.zfin.expression.Experiment;
import org.zfin.expression.Figure;
import org.zfin.framework.api.View;
import org.zfin.mutant.Fish;
import org.zfin.mutant.PhenotypeStatementWarehouse;
import org.zfin.ontology.GenericTerm;
import org.zfin.publication.Publication;

import javax.persistence.*;
import java.util.List;

/**
 * class to retrieve chebi term page - phenotype
 */
@Setter
@Getter
@Entity
@Table(name = "UI.CHEBI_PHENOTYPE_DISPLAY")
public class ChebiPhenotypeDisplay {

    @Id
    @JsonView(View.API.class)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cpd_id", nullable = false)
    private long id;

    @JsonView(View.API.class)
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cpd_fish_zdb_id")
    private Fish fish;
    @JsonView(View.API.class)
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cpd_fig_zdb_id")
    private Figure figure;
    @JsonView(View.API.class)
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cpd_pub_zdb_id")
    private Publication publication;
    @JsonView(View.API.class)
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "UI.CHEBI_PHENOTYPE_WAREHOUSE_ASSOCIATION", joinColumns = {
        @JoinColumn(name = "cpwa_phenotype_id", nullable = false, updatable = false)},
        inverseJoinColumns = {@JoinColumn(name = "cpwa_phenotype_warehouse_id",
            nullable = false, updatable = false)})
    private List<PhenotypeStatementWarehouse> phenotypeStatements;
    @JsonView(View.API.class)
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cpd_exp_zdb_id")
    private Experiment experiment;

    @JsonView(View.API.class)
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cpd_term_zdb_id")
    private GenericTerm term;

    @JsonView(View.API.class)
    @Column(name = "cpd_fig_count")
    private int numberOfFigs;

    @JsonView(View.API.class)
    @Column(name = "cpd_pub_count")
    private int numberOfPubs;

    @Column(name = "cpd_fish_search")
    private String fishSearch;

    @Column(name = "cpd_phenotype_search")
    private String phenotypeStatementSearch;

    @Column(name = "cpd_condition_search")
    private String conditionSearch;

    @Column(name = "cpd_gene_search")
    private String geneSymbolSearch;

    @Column(name = "cpd_is_multi_chebi_condition")
    private boolean multiChebiCondition;

    @JsonView(View.API.class)
    @Column(name = "cpd_ameliorated_exacerbated_phenotype_search")
    private String amelioratedExacerbatedPhenoSearch;

    @Transient
    private boolean includeSubstructures;

    public ChebiPhenotypeDisplay() {
    }

    public ChebiPhenotypeDisplay(Fish fish, GenericTerm term) {
        this.fish = fish;
        this.term = term;
    }

    public Fish getFish() {
        return fish;
    }

}
