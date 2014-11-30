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

define(['react', 'coreMixin', 'streamMixin', 'app_ds_agentname'], function (React, coreMixin, streamMixin, AgentName) {

    return React.createClass({
        mixins: [coreMixin, streamMixin],

        componentName: function() { return "app/content/ds/AgentTabs"; },


        subscriptionConfig: function (props) {
            return [{address:props.addr, route:'agents', topic:'list', dataKey: 'list', onData: this.onData}];
        },
        getInitialState: function () {
            return {list: null, selected: false}
        },

        onSelectionMade: function(id) {
            this.raiseEvent("agentSelected", {id: id});
            this.setState({selected: id});
        },



        onData: function (data) {
                var wasSelected = this.state ? this.state.selected : false;
                var newSelected = wasSelected;
                var list = data ? data : [];
                if (newSelected && !list.some(function(el) { return el.id == newSelected})) newSelected = false;
                if (!newSelected && list.length > 0) newSelected = list[0].id;
                if (newSelected != wasSelected) this.onSelectionMade(newSelected);
        },

        renderData: function() {

            var self = this;
            var props = this.props;
            var cx = React.addons.classSet;
            var connected = this.state.connected;

            var list = this.state.list ? this.state.list : [];

            if (!list || list.length == 0) {
                if (!self.state.connected) return <p className="bg-warning withspace">Waiting for the connection ...</p>;
                return <p className="bg-warning withspace">Waiting for the first agent to connect to HQ ...</p>
            }

            var selected = this.state.selected;

            return (
                <div className="withspace">
                    <ul className="nav nav-tabs" role="tablist">
                        {list.map(function (el) {

                            var tabClasses = cx({
                                'disabled': (!connected),
                                'active': selected == el.id
                            });

                            return  <li key={el.id} onClick={self.onSelectionMade.bind(self, el.id)} role="presentation" className={tabClasses}>
                                <AgentName addr={props.addr} id={el.id}/>
                            </li>;
                            })}
                    </ul>
                </div>
            );
        },

        renderLoading: function() {
            return (
                <div>loading...</div>
            );
        },

        render: function () {
            if (this.state.list) {
                return this.renderData();
            } else {
                return this.renderLoading();
            }
        }
    });

});