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

define(['react', 'coreMixin', 'app_content_nodetabs', 'app_ds_agent_content'],
    function (React, coreMixin, Tabs, AgentContent) {

        return React.createClass({
            mixins: [coreMixin],

            getInitialState: function () {
                return {selected: false, agentSelected: false}
            },

            subscribeToEvents: function () {
                return [
                    ["nodeSelectorForDatasources", this.handleSelectionEvent]
                ]
            },


            handleSelectionEvent: function (evt) {
                this.setState({selected: evt.detail.address});
            },


            render: function () {

                return <div>
                    <Tabs roles={["hq"]} selectorId="nodeSelectorForDatasources" />
                    {this.state.selected ? <AgentContent addr={this.state.selected}/> : ""}
                </div>;
            }
        });

    });