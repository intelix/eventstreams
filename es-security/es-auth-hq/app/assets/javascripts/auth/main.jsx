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

define(['react', 'core_mixin', 'common_nodetabs', './user/main', './roles/main'],
    function (React, core_mixin, Tabs, Users, Roles) {

        return React.createClass({
            mixins: [core_mixin],

            componentName: function () {
                return "auth/main";
            },

            getInitialState: function () {
                return {selected: "users"}
            },

            handleSelection: function (s) {
                this.setState({selected: s});
            },


            render: function () {

                var self = this;

                var selected = self.state.selected;

                function hasAccessToRoles() {
                    return self.hasTopicPermission("userroles", "list");
                }

                function hasAccessToUsers() {
                    return self.hasTopicPermission("users", "list");
                }

                if (!hasAccessToRoles() && selected == "roles") selected = "users";
                if (!hasAccessToUsers() && selected == "users") selected = "roles";

                var rolesClasses = this.cx({
                    'active': (self.state.selected == 'roles')
                });
                var usersClasses = this.cx({
                    'active': (self.state.selected == 'users')
                });

                var content = "";
                if (self.state.selected == 'users') {
                    content = <Users {...self.props} />;
                } else {
                    content = <Roles {...self.props} />;
                }

                var tabs = "";
                if (hasAccessToUsers) {
                    tabs +=
                        <li role="presentation" className={usersClasses}>
                            <a href="#" onClick={self.handleSelection.bind(self, "users")}>Users</a>
                        </li>;
                }
                if (hasAccessToRoles) {
                    tabs +=
                        <li role="presentation" className={rolesClasses}>
                            <a href="#" onClick={self.handleSelection.bind(self, "roles")}>Roles</a>
                        </li>
                }

                return <div>
                    <ul className="nav nav-pills">
                        {tabs}
                    </ul>
                    {content}
                </div>;
            }
        });

    });