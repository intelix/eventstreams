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

        componentName: function () {
            return "app/content/momentum/Table";
        },

        subscriptionConfig: function (props) {
            return [
                {address: props.addr, route: 'momentum', topic: 'dict_snapshot', onData: this.onDictSnapshot},
                {address: props.addr, route: 'momentum', topic: 'values:*', onData: this.onValues}
            ];
        },
        getInitialState: function () {
            return {dict: {}}
        },

        handleAddNew: function () {
            this.raiseEvent("addFlow", {});
        },


        parseDict: function(map, data) {
            data.forEach(function(x) {
                map[x.id] = x;
            });
            return map;
        },

        onDictSnapshot: function(data) {
            this.setState({dict: this.parseDict({}, data)});
        },
        onValues: function(data) {
            this.logInfo("!>>> onValues: " + JSON.stringify(data));
        },

        renderData: function () {
            var props = this.props;

            return (
                <div>
                    <h3>Application</h3>
                    <table className="table table-condensed">
                        <thead>
                            <th>Sensor</th>
                            <th width="10px">Value</th>
                            <th>Trends</th>
                        </thead>
                    </table>
                </div>
            );
        },
        renderLoading: function () {
            return (
                <div>loading...</div>
            );
        },

        render: function () {
            if (this.state.dict) {
                return this.renderData();
            } else {
                return this.renderLoading();
            }
        }
    });

});