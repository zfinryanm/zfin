import React, {useState} from 'react';
import PropTypes from 'prop-types';
import qs from 'qs';
import DataTable from '../components/data-table';
import {EntityLink, EntityList} from '../components/entity';
import FigureSummary from '../components/FigureSummary';
import DataTableSummaryToggle from '../components/DataTableSummaryToggle';

const TermAntibodyTable = ({termId, directAnnotationOnly}) => {

    const [directAnnotation, setDirectAnnotation] = useState(directAnnotationOnly === 'true');
    const [count, setCount] = useState({'countDirect': 0, 'countIncludingChildren': 0});

    const columns = [
        {
            label: 'Antibody',
            content: ({antibody}) => <EntityLink entity={antibody}/>,
            filterName: 'antibodyName',
            width: '150px',
        },
        {
            label: 'Gene',
            content: ({antigenGeneList}) => <EntityList entities={antigenGeneList}/>,
            filterName: 'geneName',
            width: '120px',
        },
        {
            label: 'Term',
            content: ({term}) => <EntityLink entity={term}/>,
            filterName: 'termName',
            width: '120px',
        },
        {
            label: 'Figures',
            content: row => (
                <FigureSummary
                    statistics={row}
                    allFiguresUrl={`/action/antibody/antibody-figure-summary?superTermID=${row.term.zdbID}&antibodyID=${row.antibody.zdbID}`}
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
                dataUrl={`/action/api/ontology/${termId}/antibodies?${qs.stringify(params)}`}
                onDataLoadedCount={(count) => setCount(count)}
                rowKey={row => row.antibody.zdbID}
            />
        </>
    );
};

TermAntibodyTable.propTypes = {
    termId: PropTypes.string,
    directAnnotationOnly: PropTypes.string,
};

export default TermAntibodyTable;
