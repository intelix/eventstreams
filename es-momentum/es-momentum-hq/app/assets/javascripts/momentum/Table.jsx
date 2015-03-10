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
                {address: props.addr, route: 'momentum', topic: 'dict_update', onData: this.onDictUpdate},
                {address: props.addr, route: 'momentum', topic: 'values:*', onData: this.onValues}
            ];
        },
        getInitialState: function () {
            return {list: null}
        },

        handleAddNew: function () {
            this.raiseEvent("addFlow", {});
        },

        onDictSnapshot: function(data) {
            this.logInfo("!>>> onDictSnapshot: " + JSON.stringify(data));
        },
        onDictUpdate: function(data) {
            this.logInfo("!>>> onDictUpdate: " + JSON.stringify(data));
        },
        onValues: function(data) {
            this.logInfo("!>>> onValues: " + JSON.stringify(data));
        },

        renderData: function () {
            var props = this.props;

            return (
                <div>
                    Hello momentum
                </div>
            );
        },
        renderLoading: function () {
            return (
                <div>loading...</div>
            );
        },

        render: function () {
            return (
                <div>Hello momentum</div>
            );
        }
    });

});