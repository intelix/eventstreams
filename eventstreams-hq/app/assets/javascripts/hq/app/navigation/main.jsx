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

define(
    ['react',
        'core_mixin',
        './NavbarElement'],
    function (React,
              core_mixin,
              Element) {

        return React.createClass({
            mixins: [core_mixin],

            componentName: function() { return "app/navigation/Navbar"; },

            getInitialState: function () {
                return {list: []}
            },

            subscriptionConfig: function (props) {
                return [{
                    address: 'local',
                    route: "unsecured_hqgroups",
                    topic: 'list',
                    dataKey: 'list'
                }];
            },



            render: function () {
                var self = this;
                var servicesList = !this.state.list || !this.state.list.services ? [] : this.state.list.services;
                var allowedList = servicesList.map(function(next) {
                    return next.id && self.hasDomainPermission(next.id) ? next.group : false;
                }).filter(function(next) {
                    return next;
                });
                var list =  (!this.state.list || !this.state.list.groups ? [] : this.state.list.groups).filter(function(next) { return $.inArray(next, allowedList) > -1; });

                var defaultActive = list.length > 0 ? list[0] : "";

                return <nav className="navbar navbar-inverse navbar-fixed-top" role="navigation">
                    <div className="container-fluid">

                        <div className="navbar-header">
                            <a className="navbar-brand" href="#">
                                eventstreams
                                <b>HQ</b>
                            </a>
                        </div>
                        <div id="navbar" className="navbar-collapse collapse">
                            <ul className="nav navbar-nav">
                            {list.map(function(el) {
                                return <Element groupKey={el} name={el} key={el} active={defaultActive == el} />;
                            } )}
                            </ul>
                        </div>
                    </div>
                </nav>;
            }
        });

    });