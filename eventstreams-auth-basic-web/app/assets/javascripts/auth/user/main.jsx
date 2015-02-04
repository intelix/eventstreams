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

define(['react', 'core_mixin', 'common_nodetabs', './Table', './Editor'],
    function (React, core_mixin, Tabs, Table, Editor) {

        return React.createClass({
            mixins: [core_mixin],

            componentName: function() { return "app/content/user_management/main"; },

            getInitialState: function () {
                return {selected: false}
            },

            subscribeToEvents: function() { return [
                ["addUser", this.openModal],
                ["editUser", this.openEditModal],
                ["usersModalClosed", this.closeModal],
                ["nodeSelectorForAuth", this.handleSelectionEvent]
            ]},

            openModal: function (evt) {
                var defaults = {
                    "name": "",
                    "password": "",
                    "passwordHash": "",
                    "roles": ""
                };
                this.setState({editBlock: <Editor addr={this.state.selected} mgrRoute="users" title="User configuration" defaults={defaults}  editorId="users"/>});
            },
            openEditModal: function (evt) {
                this.setState({editBlock: <Editor addr={this.state.selected} mgrRoute="users" ckey={evt.detail.ckey} title="User configuration"  editorId="users"/>});
            },
            closeModal: function () {
                this.setState({editBlock: false});
            },

            handleSelectionEvent: function (evt) {
                this.setState({selected: evt.detail.address});
            },


            render: function () {

                return <div>

                    <Tabs roles={["hq"]} selectorId="nodeSelectorForAuth" hideIfSingle="true"/>
                    {this.state.editBlock ? this.state.editBlock : ""}
                    {this.state.selected ? <Table addr={this.state.selected}/> : ""}
                </div>;
            }
        });

    });