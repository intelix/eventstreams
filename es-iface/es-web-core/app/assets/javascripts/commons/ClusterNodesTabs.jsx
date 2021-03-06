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

define(['react', 'core_mixin'], function (React, core_mixin) {

    return React.createClass({
        mixins: [core_mixin],

        componentName: function() { return "app/content/commons/ClusterNodeTabs"; },

        subscriptionConfig: function (props) {
            return [{address: 'local', route: 'unsecured_cluster', topic: 'nodes', dataKey: 'nodes', onData: this.onData}];
        },
        getInitialState: function () {
            return {nodes: null, selected: false}
        },

        onSelectionMade: function (address) {
            this.raiseEvent(this.props.selectorId, {address: address});
            this.setState({selected: address});
        },

        filteredNodes: function (data) {
            var self = this;

            function onlyRequiredRoles(el) {
                return self.props.roles.filter(function (requiredRole) {
                        return $.inArray(requiredRole, el.roles) > -1;
                    }).length != 0;
            }

            return data ? data.filter(onlyRequiredRoles) : [];
        },

        onData: function (data) {
            var wasSelected = this.state ? this.state.selected : false;
            var newSelected = wasSelected;
            var filteredNodes = this.filteredNodes(data);
            if (newSelected && !filteredNodes.some(function (el) {
                    return el.address == newSelected
                })) newSelected = false;
            if (!newSelected && filteredNodes.length > 0) newSelected = filteredNodes[0].address;

            if (newSelected != wasSelected) this.onSelectionMade(newSelected);
        },

        renderData: function () {

            var self = this;
            var cx = React.addons.classSet;
            var connected = this.state.connected;

            if (!this.state.nodes || this.state.nodes.length == 0) {
                if (!this.state.connected) return <p className="bg-warning">Waiting for the connection ...</p>
                return <p className="bg-warning">Waiting for the console to join the Cluster ...</p>
            }

            var filteredNodes = this.filteredNodes(this.state.nodes);

            if (filteredNodes.length == 0) {
                if (!this.state.connected) return <p className="bg-warning">Waiting for the connection ...</p>
                return <p className="bg-warning">Waiting for first {self.props.nodeName} node to join the Cluster ...</p>
            }

            var selected = this.state.selected;

            if (self.props.hideIfSingle && filteredNodes.length == 1) {
                return <span></span>;
            }
            
            return (
                <div>
                    <ul className="nav nav-tabs" role="tablist">
                        {filteredNodes.map(function (el) {

                            var tabClasses = cx({
                                'disabled': (!connected || el.state != 'up'),
                                'active': selected == el.address
                            });

                            return <li key={el.id} onClick={self.onSelectionMade.bind(self, el.address)} role="presentation" className={tabClasses}>
                                <a href="#">{el.name} ({el.state})</a>
                            </li>;
                        })}
                    </ul>
                </div>
            );
        },

        renderLoading: function () {
            return (
                <div>loading...</div>
            );
        },

        render: function () {
            if (this.state.nodes) {
                return this.renderData();
            } else {
                return this.renderLoading();
            }
        }
    });

});