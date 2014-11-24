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
define(['react', 'coreMixin', 'subscriberMixin', 'app_gates_table_row'], function (React, coreMixin, subscriberMixin, Row) {

    return React.createClass({
        mixins: [coreMixin, subscriberMixin],

        subscriptionConfig: function (props) {
            return {address: props.addr, route: 'gates', topic: 'list', target: 'list'};
        },
        getInitialState: function () {
            return {list: null}
        },

        handleAddNew: function() {
            this.raiseEvent("addNewGate", {});
        },

        renderData: function () {
            var props = this.props;

            function header() {
                return <tr>
                    <th>Name</th>
                    <th>Retention</th>
                    <th>Retained data</th>
                    <th>In-flight</th>
                    <th>Current rate</th>
                    <th>Avg events/day</th>
                    <th>Active datasources</th>
                    <th>Active sinks</th>
                    <th>Inactive sinks</th>
                    <th>Created</th>
                    <th>Last state change</th>
                    <th>State</th>
                    <th></th>
                </tr>;
            }

            function row(el) {
                return <Row {...props} key={el.id} id={el.id} />;
            }

            var addButton = <div className="withspace">
                <button type="button" className={"btn btn-default btn-sm "} onClick={this.handleAddNew} >
                Add new
                </button>
            </div>;

            return (
                <div>
                    {addButton}

                    <table className="table table-striped">
                        <thead>
                        {header()}
                        </thead>
                        <tbody>
                        {this.state.list.map(row)}
                        </tbody>

                    </table>
                </div>
            );
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