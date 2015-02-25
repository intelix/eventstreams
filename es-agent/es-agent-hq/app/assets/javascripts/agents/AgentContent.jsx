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

define(['react', 'core_mixin', './AgentsTabs', './EventsourcesTable', './EventsourcesEditor'],
    function (React, core_mixin, AgentsTabs, Table, EditBlock) {

        return React.createClass({
            mixins: [core_mixin],

            componentName: function() { return "app/content/ds/AgentContent"; },

            getInitialState: function () {
                return {agentSelected: false }
            },

            subscribeToEvents: function() { return [
                ["addEventsource", this.openModal],
                ["editEventsource", this.openEditModal],
                ["esourceModalClosed", this.closeModal],
                ["agentSelected", this.handleAgentSelectionEvent],
            ]},

            openModal: function (evt) {
                var defaults = {
                    "name": "",
                    "desc": "",
                    "streamKey": "",
                    "tags": "",
                    "targetGate": "akka.tcp://hub@localhost:12345/user/GATENAME"
                };
                this.setState({editBlock: <EditBlock addr={this.props.addr} mgrRoute={this.state.agentSelected}  title="Eventsource configuration" defaults={defaults} editorId="esource"/>});
            },
            openEditModal: function (evt) {
                this.setState({editBlock: <EditBlock addr={this.props.addr} mgrRoute={this.state.agentSelected} ckey={evt.detail.ckey} title="Eventsource configuration"  editorId="esource"/>});
            },
            closeModal: function () {
                this.setState({editBlock: false});
            },

            handleAgentSelectionEvent: function (evt) {
                this.setState({agentSelected: evt.detail.ckey});
            },


            render: function () {

                return <div>
                    <AgentsTabs {...this.props} />
                    {this.state.editBlock ? this.state.editBlock : ""}
                    {this.state.agentSelected ? <Table ckey={this.state.agentSelected} {...this.props}/> : ""}
                </div>;
            }
        });

    });