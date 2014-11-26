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

define(['react', 'coreMixin', 'app_content_nodetabs', 'app_gates_table', 'app_gates_editor'],
    function (React, coreMixin, Tabs, Table, EditBlock) {

        return React.createClass({
            mixins: [coreMixin],

            getInitialState: function () {
                return {selected: false}
            },

            subscribeToEvents: function() { return [
                ["addGate", this.openModal],
                ["editGate", this.openEditModal],
                ["modalClosed", this.closeModal],
                ["nodeSelectorForGates", this.handleSelectionEvent]
            ]},

            openModal: function (evt) {
                var defaults = {
                    "name": "",
                    "initialState": "Closed",
                    "maxInFlight": 100,
                    "overflowPolicy": {
                        "type": "backpressure"
                    }
                };
                this.setState({editBlock: <EditBlock addr={this.state.selected} addRoute="gates" title="Gate configuration" defaults={defaults} />});
            },
            openEditModal: function (evt) {
                this.setState({editBlock: <EditBlock addr={this.state.selected} id={evt.detail.id} title="Gate configuration"/>});
            },
            closeModal: function () {
                this.setState({editBlock: false});
            },

            handleSelectionEvent: function (evt) {
                this.setState({selected: evt.detail.address});
            },


            render: function () {

                return <div>

                    <Tabs roles={["hq"]} selectorId="nodeSelectorForGates" />
                    {this.state.editBlock ? this.state.editBlock : ""}
                    {this.state.selected ? <Table addr={this.state.selected}/> : ""}
                </div>;
            }
        });

    });