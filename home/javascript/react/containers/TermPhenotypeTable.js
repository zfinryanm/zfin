import React, {useState} from 'react';
import PropTypes from 'prop-types';
import qs from 'qs';
import DataTable from '../components/data-table';
import {EntityList} from '../components/entity';
import DataTableSummaryToggle from '../components/DataTableSummaryToggle';
import CommaSeparatedList from '../components/CommaSeparatedList';
import PhenotypeStatementLink from '../components/entity/PhenotypeStatementLink';
import FigureSummaryPhenotype from '../components/FigureSummaryPhenotype';

const TermPhenotypeTable = ({termId, directAnnotationOnly, endpointUrl = 'phenotype'}) => {

    const [directAnnotation, setDirectAnnotation] = useState(directAnnotationOnly === 'true');
    const [count, setCount] = useState({'countDirect': 0, 'countIncludingChildren': 0});

    const columns = [
        {
            label: 'Affected Genomic Region',
            content: ({affectedGenes}) => <EntityList entities={affectedGenes}/>,
            filterName: 'geneSymbol',
            width: '100px',
        },
        {
            label: 'Fish',
            content: ({fish}) => <a
                href={'/' + fish.zdbID}
                dangerouslySetInnerHTML={{__html: fish.name}}
            />,
            filterName: 'fishName',
            width: '120px',
        },
        {
            label: 'Phenotype',
            content: ({phenotypeStatements}) => <CommaSeparatedList>
                {phenotypeStatements.map(entity => {
                    return <PhenotypeStatementLink key={entity.id} entity={entity}/>
                })}
            </CommaSeparatedList>,
            filterName: 'phenotype',
            width: '220px',
        },
        {
            label: 'Term',
            content: ({term}) => <a href={'/' + term.zdbID}>{term.termName}</a>,
            filterName: 'termName',
            width: '120px',
        },
        {
            label: 'Figures',
            content: row => (
                <FigureSummaryPhenotype
                    statistics={row}
                    allFiguresUrl={`/action/ontology/${row.term.zdbID}/phenotype-summary/${row.fish.zdbID}`}
                />
            ),
            width: '100px',
        },

    ];

    const params = {};
    if (directAnnotation) {
        params.directAnnotation = true;
    }

    return (
        <>
            {directAnnotationOnly && count.countIncludingChildren > 0 && (
                <DataTableSummaryToggle
                    showPopup={directAnnotation}
                    directCount={count.countDirect}
                    childrenCount={count.countIncludingChildren}
                    onChange={setDirectAnnotation}
                />
            )}
            <DataTable
                columns={columns}
                dataUrl={`/action/api/ontology/${termId}/${endpointUrl}?${qs.stringify(params)}`}
                onDataLoadedCount={(count) => setCount(count)}
                rowKey={row => row.fish.zdbID}
            />
        </>
    );
};

TermPhenotypeTable.propTypes = {
    termId: PropTypes.string,
    endpointUrl: PropTypes.string,
    directAnnotationOnly: PropTypes.string,
};

export default TermPhenotypeTable;
