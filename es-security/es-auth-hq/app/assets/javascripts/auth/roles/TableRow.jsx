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

define(['react', 'core_mixin', 'common_statelabel', 'common_rate', 'common_yesno', 'common_button_delete'],
    function (React, core_mixin, StateLabel, Rate, YesNo, DeleteButton) {

        return React.createClass({
            mixins: [core_mixin],

            componentName: function () {
                return "auth/roles/TableRow/" + this.props.ckey;
            },

            subscriptionConfig: function (props) {
                return [
                    {address: props.addr, route: props.ckey, topic: 'info', dataKey: 'info'}
                ];
            },
            getInitialState: function () {
                return {info: false}
            },

            handleRowClick: function () {
                this.raiseEvent("editUserRole", {ckey: this.props.ckey});
            },

            renderData: function () {
                var self = this;

                var info = self.state.info;


                var mainLink = info.name;
                if (self.state.connected) {
                    mainLink = <a href="#" onClick={this.handleRowClick} >{mainLink}</a>;
                }


                return (
                    <tr ref='monitorVisibility' >
                        <td>{mainLink}</td>
                        <td>{info.permissions}</td>
                        <td>
                            <DeleteButton {...this.props} />
                        </td>
                    </tr>
                );


            },
            renderLoading: function () {
                return (
                    <tr>
                        <td colSpan="100%">Loading...</td>
                    </tr>
                );
            },

            render: function () {
                if (this.state.info) {
                    return this.renderData();
                } else {
                    return this.renderLoading();
                }
            }
        });

    });