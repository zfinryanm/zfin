import React, {useState} from 'react';
import PropTypes from 'prop-types';
import qs from 'qs';
import DataTable from '../components/data-table';
import DataTableSummaryToggle from '../components/DataTableSummaryToggle';
import CommaSeparatedList from '../components/CommaSeparatedList';
import PhenotypeStatementLink from '../components/entity/PhenotypeStatementLink';
import FigureSummaryPhenotype from '../components/FigureSummaryPhenotype';

const ChebiModifiedPhenotypeTable = ({termId, directAnnotationOnly, endpointUrl = 'phenotype-chebi'}) => {

    const [directAnnotation, setDirectAnnotation] = useState(directAnnotationOnly === 'true');
    const [count, setCount] = useState({'countDirect': 0, 'countIncludingChildren': 0});

    const columns = [
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
            label: 'Modification',
            content: ({amelioratedExacerbatedPhenoSearch}) => <>{amelioratedExacerbatedPhenoSearch}</>,
            filterName: 'modification',
            width: '120px',
        },
        {
            label: 'Conditions',
            content: (row) => <span className='text-break'>
                <a
                    className='text-break'
                    href={`/${row.experiment.zdbID}`}
                    dangerouslySetInnerHTML={{__html: row.experiment.conditions}}
                />
                <a
                    className='popup-link data-popup-link'
                    href={`/action/expression/experiment-popup?id=${row.experiment.zdbID}`}
                />
            </span>,
            filterName: 'conditionName',
            width: '300px',
        },
        {
            label: 'Phenotype',
            content: ({phenotypeStatements}) => <CommaSeparatedList>
                {phenotypeStatements.map(entity => {
                    return <PhenotypeStatementLink key={entity.id} entity={entity}/>
                })}
            </CommaSeparatedList>,
            filterName: 'phenotype',
            width: '300px',
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
    params.isAmelioratedExacerbated = true;

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

ChebiModifiedPhenotypeTable.propTypes = {
    termId: PropTypes.string,
    endpointUrl: PropTypes.string,
    directAnnotationOnly: PropTypes.string,
};

export default ChebiModifiedPhenotypeTable;
