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

define(['react', 'core_mixin', 'common_tabs', './config/main', './sensors/main'],
    function (React, core_mixin, Tabs, ConfigurationMain, SensorsMain) {

        return React.createClass({
            mixins: [core_mixin],

            componentName: function () {
                return "app/content/gates/TableRowSelected/" + this.props.ckey;
            },

            getInitialState: function() {
                return { selected: 'config' }
            },

            subscribeToEvents: function () {
                return [
                    ["gateExplorerTabSelected", this.handleSelection]
                ]
            },

            handleSelection: function(evt) {
              this.setState({selected: evt.detail.tabkey});
            },

            getTabsList: function() {
                return [
                    {
                        tabkey: 'config',
                        title: 'Configuration'
                    },
                    {
                        tabkey: 'sensors',
                        title: 'Sensors'
                    },
                    {
                        tabkey: 'events',
                        title: 'Events explorer'
                    }
                ];
            },

            renderData: function () {
                var self = this;

                var selectedContents;
                if (!self.state.selected || self.state.selected == 'config') selectedContents = <ConfigurationMain   {...self.props} />;
                if (self.state.selected == 'sensors') selectedContents = <SensorsMain   {...self.props}/>;
                if (self.state.selected == 'events') selectedContents = <SensorsMain   {...self.props}/>;

                return <tr className="expanded">
                    <td colSpan="100%" className="expanded">

                        <div className="container-fluid">

                            <div className="row">
                                <div className="col-md-12">
                                    <Tabs eventId="gateExplorerTabSelected" getTabsList={self.getTabsList} noDataMessage="" selected={self.state.selected} />
                                </div>
                            </div>

                        {selectedContents}

                        </div>

                    </td>
                </tr>;

            },

            render: function () {
              return this.renderData();
            }
        });

    });