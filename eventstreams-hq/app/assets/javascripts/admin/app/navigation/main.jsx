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
        './NavbarGates',
        './NavbarFlows',
        './NavbarDatasources',
        './NavbarTools'],
    function (React,
              core_mixin,
              Gates,
              Flows,
              Datasources,
              Tools) {

        return React.createClass({
            mixins: [core_mixin],

            componentName: function() { return "app/navigation/Navbar"; },



            render: function () {
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
                                <Gates active={true} ckey="gates"/>
                                <Flows ckey="flows"/>
                                <Datasources  ckey="datasources"/>
                                <Tools ckey="tools"/>
                            </ul>
                        </div>
                    </div>
                </nav>;
            }
        });

    });