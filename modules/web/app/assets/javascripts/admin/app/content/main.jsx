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

define(
    ['react',
        'core_mixin',
        './gates/main',
        './flows/main',
        './ds/main',
        './notif/main'],
    function (React,
              core_mixin,
              Gates,
              Flows,
              Datasources,
              Notif) {

        return React.createClass({
            mixins: [core_mixin],

            componentName: function() { return "app/content/ContentManager"; },


            getInitialState: function () {
                return { selection: "gates" }
            },

            componentDidMount: function() {
                this.addEventListener("navBarSelection", this.handleSelectionEvent);
            },

            componentWillUnmount: function() {
                this.removeEventListener("navBarSelection", this.handleSelectionEvent);
            },

            handleSelectionEvent: function(evt) {
                this.setState({selection: evt.detail.ckey});
            },

            render: function () {

                var selection = false;

                switch (this.state.selection) {
                    case 'gates': selection = <Gates />; break;
                    case 'flows': selection = <Flows />; break;
                    case 'datasources': selection = <Datasources />; break;
                    case 'notif': selection = <Notif />; break;
                    default: selection = "";
                }

                return <div className="container-fluid main-container" role="main"><span>{selection}</span></div>
            }
        });

    });