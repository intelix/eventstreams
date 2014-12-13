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

define(['react', 'core_mixin', 'common_nodetabs', './FlowsTable', './FlowsEditor'],
    function (React, core_mixin, Tabs, Table, EditBlock) {

        return React.createClass({
            mixins: [core_mixin],

            componentName: function() { return "app/content/flows/Content"; },

            getInitialState: function () {
                return {selected: false}
            },

            subscribeToEvents: function() { return [
                ["addFlow", this.openModal],
                ["editFlow", this.openEditModal],
                ["flowModalClosed", this.closeModal],
                ["nodeSelectorForFlows", this.handleSelectionEvent]
            ]},

            openModal: function (evt) {
                var defaults = {
                    "name": "",
                    "desc": "",
                    "initialState": "Started",
                    "tap": {
                        "class": "gate",
                        "name": ""
                    },
                    "pipeline": []
                };
                this.setState({editBlock: <EditBlock addr={this.state.selected} addRoute="flows" title="Flow configuration" defaults={defaults}  editorId="flow"/>});
            },
            openEditModal: function (evt) {
                this.setState({editBlock: <EditBlock addr={this.state.selected} ckey={evt.detail.ckey} title="Flow configuration"  editorId="flow"/>});
            },
            closeModal: function () {
                this.setState({editBlock: false});
            },

            handleSelectionEvent: function (evt) {
                this.setState({selected: evt.detail.address});
            },


            render: function () {

                return <div>

                    <Tabs roles={["hq"]} selectorId="nodeSelectorForFlows" />
                    {this.state.editBlock ? this.state.editBlock : ""}
                    {this.state.selected ? <Table addr={this.state.selected}/> : ""}
                </div>;
            }
        });

    });