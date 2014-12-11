/*
 * Copyright 2014 Intelix Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
define(['react', 'coreMixin', 'streamMixin', 'paginationMixin', 'app_gates_table_row'], function (React, coreMixin, streamMixin, paginationMixin, Row) {

    return React.createClass({
        mixins: [coreMixin, streamMixin, paginationMixin],

        componentName: function () {
            return "app/content/gates/Table";
        },

        subscriptionConfig: function (props) {
            return [{address: props.addr, route: 'gates', topic: 'list', dataKey: 'list'}];
        },
        getInitialState: function () {
            return {
                list: null,
                pageSize: 10,
                page: 1
            }
        },

        subscribeToEvents: function () {
            return [
                ["tableRowClicked", this.handleTableRowClick]
            ]
        },

        handleTableRowClick: function (evt) {
            this.setState({tableShrinked: !this.state.tableShrinked, selectedItem: evt.detail.id});
        },

        handleAddNew: function () {
            this.raiseEvent("addGate", {});
        },

        renderData: function () {
            var props = this.props;
            var self = this;

            var shrinked = self.state.tableShrinked;

            var header;
            if (!shrinked) {
                header = <tr>
                    <th>Name</th>
                    <th>Address</th>
                    <th>Retention</th>
                    <th>Retained</th>
                    <th>Overflow</th>
                    <th>Sink req</th>
                    <th>In-flight</th>
                    <th>Current</th>
                    <th>Mean</th>
                    <th>Sources</th>
                    <th>Sinks</th>
                    <th>Created</th>
                    <th>State changed</th>
                    <th>State</th>
                    <th></th>
                </tr>;
            } else {
                header = <tr>
                    <th>Name</th>
                    <th>State</th>
                </tr>;
            }


            function row(el) {
                return <Row {...props} key={el.id} id={el.id} shrinked={shrinked} selected={el.id == self.state.selectedItem}/>;
            }

            var buttonClasses = this.cx({
                'disabled': (!self.state.connected)
            });

            var addButton = <div className="withspace">
                <button type="button" className={"btn btn-default btn-sm " + buttonClasses} onClick={self.handleAddNew} >
                    Add new
                </button>
            </div>;

            var pagination = self.renderPagination();

            var tableContent = <span>
                {addButton}

                <table  className="table table-striped table-hover" >
                    <thead>
                        {header}
                    </thead>
                    <tbody >
                        {self.itemsOnCurrentPage().map(row)}
                    </tbody>
                </table>

                {pagination}
            </span>;



            if (shrinked) {
                return (
                    <div className="row">
                        <div className="col-md-2">
                        {tableContent}
                        </div>
                        <div className="col-md-5">
                            <div className="panel panel-default withspace info">
                                <div className="panel-heading">Panel heading</div>

                                <table className="table">
                                    ...
                                </table>
                            </div>
                        </div>
                        <div className="col-md-5">
                            <div className="panel panel-default withspace">
                                <div className="panel-heading">Panel heading</div>

                                <table className="table">
                                    ...
                                </table>
                            </div>
                        </div>
                    </div>
                );
            } else {
                return (
                    <div className="row">
                        <div className="col-md-12">
                        {tableContent}
                        </div>
                    </div>
                );
            }
        },
        renderLoading: function () {
            return (
                <div>loading...</div>
            );
        },

        render: function () {
            if (this.state.list) {
                return this.renderData();
            } else {
                return this.renderLoading();
            }
        }
    });

});