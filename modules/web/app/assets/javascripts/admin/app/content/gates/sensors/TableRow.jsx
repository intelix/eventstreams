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

define(['react', 'core_mixin', 'common_statelabel', 'common_rate', 'common_yesno'],
    function (React, core_mixin, StateLabel, Rate, YesNo) {

        return React.createClass({
            mixins: [core_mixin],

            componentName: function () {
                return "app/content/gates/sensors/TableRow/" + this.props.ckey;
            },

            subscriptionConfig: function (props) {
                return [
                    {address: props.addr, route: props.ckey, topic: 'info', dataKey: 'info'},
                    {address: props.addr, route: props.ckey, topic: 'stats', dataKey: 'stats'}
                ];
            },
            getInitialState: function () {
                return {info: false, stats: false}
            },

            handleRowClick: function () {
                this.raiseEvent("sensorRowSelected", {ckey: this.props.ckey});
            },

            renderData: function () {
                var self = this;

                var info = self.state.info;
                var stats = self.state.stats;

                var rowClasses = this.cx({
                    'clickable': true,
                    'warning': self.props.selected
                });

                return (
                    <tr className={rowClasses} ref='monitorVisibility' onClick={self.handleRowClick}>
                        <td>{info.name}</td>
                        <td>{info.class}</td>
                        <td>{info.subclass}</td>
                        <td>{info.level}</td>
                        <td>{stats.current}</td>
                        <td>{stats.total}</td>
                        <td><StateLabel state={info.state} details={info.stateDetails} /></td>
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