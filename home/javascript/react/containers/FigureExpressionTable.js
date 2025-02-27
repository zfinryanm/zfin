import React from 'react';
import PropTypes from 'prop-types';
import DataTable from '../components/data-table';
import {EntityLink} from '../components/entity';
import StageRange from '../components/StageRange';
import PostComposedEntity from '../components/PostComposedEntity';
import Figure from '../components/Figure';


const FigureExpressionTable = ({url, hideFigureColumn = false, navigationCounter, title}) => {

    const columns = [
        {
            label: 'Gene',
            content: row => <EntityLink entity={row.gene}/>,
            width: '150px',
            filterName: 'geneAbbreviation',
        },
        {
            label: 'Antibody',
            content: row => {
                row.antibody && <EntityLink entity={row.antibody}/>
            },
            filterName: 'antibody',
            width: '150px',
        },
        {
            label: 'Fish',
            content: (row) => <span className='text-break'>
                <a
                    className='text-break'
                    href={`/${row.fish.zdbID}`}
                    dangerouslySetInnerHTML={{__html: row.fish.displayName}}
                />
                <a
                    className='popup-link data-popup-link'
                    href={`/action/fish/fish-detail-popup/${row.fish.zdbID}`}
                />
            </span>,
            filterName: 'fish',
            width: '150px',
        },
        {
            label: 'Experiment',
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
            filterName: 'experiment',
            width: '150px',
        },
        {
            label: 'Stage',
            content: (row) => <StageRange start={row.start} end={row.end}/>,
            filterName: 'stage',
            width: '150px',
        },
        {
            label: 'Qualifier',
            content: row =>
                row.qualifier
            ,
            width: '150px',
        },
        {
            label: 'Anatomy',
            content: row => <PostComposedEntity postComposedEntity={row.entity}/>,
            filterName: 'anatomy',
            width: '150px',
        },
        {
            label: 'Assay',
            content: row => row.assay.abbreviation,
            filterName: 'assay',
            width: '150px',
        },
        {
            label: 'Figure',
            content: (row) => <Figure figure={row.figure}/>,
            hidden: hideFigureColumn
        },
    ];

    const handleDataLoadedCount = (data) => {
        if (navigationCounter && navigationCounter.setCounts && data.total) {
            navigationCounter.setCounts(title, data.total);
        }
    };

    return (
        <DataTable
            columns={columns}
            dataUrl={url}
            rowKey={() => Math.random()}
            pagination={true}
            onDataLoaded={handleDataLoadedCount}
        />
    );
};

FigureExpressionTable.propTypes = {
    url: PropTypes.string,
    title: PropTypes.string,
    hideFigureColumn: PropTypes.bool,
    navigationCounter: PropTypes.shape({
        setCounts: PropTypes.func
    }),
};

export default FigureExpressionTable;
