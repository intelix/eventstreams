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

        componentName: function() { return "app/content/commons/Tabs/~" + this.props.eventId; },


        getInitialState: function () {
            return {selected: false}
        },

        getTabsList: function() {
            return this.props.getTabsList();
        },


        onSelectionMade: function(tabkey) {
            this.raiseEvent(this.props.eventId, {tabkey: tabkey});
            this.setState({selected: tabkey});
        },



        onData: function (data) {
                var wasSelected = this.state ? this.state.selected : false;
                var list = data ? data : [];
                if (!wasSelected && list.length > 0) wasSelected = list[0].tabkey;
                var newSelected = wasSelected;
                if (newSelected && !list.some(function(el) { return el.tabkey == newSelected})) newSelected = false;
                if (!newSelected && list.length > 0) newSelected = list[0].tabkey;
                if (newSelected != wasSelected) this.onSelectionMade(newSelected);
        },




        renderData: function() {

            var self = this;
            var props = this.props;
            var cx = React.addons.classSet;
            var connected = this.state.connected;

            var list = self.getTabsList();
            if (!list) list = [];

            if (!list || list.length == 0) {
                if (!self.state.connected) return <p className="bg-warning withspace">Waiting for the connection ...</p>;
                return <p className="bg-warning withspace">{self.props.noDataMessage}</p>
            }

            var selected = this.state.selected;

            return (
                    <ul className="nav nav-tabs" role="tablist">
                        {list.map(function (el) {

                            var tabClasses = cx({
                                'disabled': (!connected),
                                'active': selected == el.tabkey
                            });

                            return  <li key={el.tabkey} onClick={self.onSelectionMade.bind(self, el.tabkey)} role="presentation" className={tabClasses}>
                                <a href="#">{el.title}</a>
                            </li>;
                            })}
                    </ul>
            );
        },

        renderLoading: function() {
            return (
                <div>loading...</div>
            );
        },

        render: function () {
            if (this.getTabsList()) {
                return this.renderData();
            } else {
                return this.renderLoading();
            }
        }
    });

});