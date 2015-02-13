/*
 * Copyright 2014-15 Intelix Pty Ltd
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

define(['react', 'core_mixin', 'common_nodetabs', './AgentContent'],
    function (React, core_mixin, Tabs, AgentContent) {

        return React.createClass({
            mixins: [core_mixin],

            componentName: function() { return "app/content/ds/Content"; },

            getInitialState: function () {
                return {selected: false, agentSelected: false}
            },

            subscribeToEvents: function () {
                return [
                    ["nodeSelectorForEventsources", this.handleSelectionEvent]
                ]
            },


            handleSelectionEvent: function (evt) {
                this.setState({selected: evt.detail.address});
            },


            render: function () {

                return <div>
                    <Tabs roles={["hq"]} selectorId="nodeSelectorForEventsources" nodeName="HQ" />
                    {this.state.selected ? <AgentContent addr={this.state.selected}/> : ""}
                </div>;
            }
        });

    });