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


    var X = React.createClass({
        mixins: [core_mixin],

        componentName: function () {
            return "app/content/gauges/GroupSelectors/" + this.props.level;
        },

        getInitialState: function () {
            return {selected: []}
        },

        handleCheckboxTick: function (id) {
            var newArr = this.state.selected ? this.state.selected : [];
            if ($.inArray(id, newArr) > -1) {
                newArr = newArr.filter(function (x) {
                    return x != id;
                });
                this.raiseEvent("gauges_group_unselect", {idx: this.props.level, v: id, id: this.props.groupId});
            } else {
                newArr.push(id);
                this.raiseEvent("gauges_group_select", {idx: this.props.level, v: id, id: this.props.groupId});
            }
            this.setState({selected: newArr ? newArr : []});
        },

        render: function () {
            var self = this;
            var props = self.props;
            var groupId = props.groupId;

            var groups = props.groups;
            var level = props.level;
            if (!level) level = 0;

            var currentSelection = self.state.selected;

            var toDisplay = [];
            groups
                .map(function (next) {
                    return next && next[groupId] && next[groupId].length > level ? next[groupId][level] : false;
                }).forEach(function (next) {
                    if (next && $.inArray(next, toDisplay) < 0) toDisplay.push(next);
                });

            var filtered = groups.filter(function (next) {
                return next && next[groupId] && next[groupId].length > level && next[groupId][level] && ($.inArray(next[groupId][level], currentSelection) > -1);
            });

            function toRow(v) {
                var selected = $.inArray(v, currentSelection) > -1;
                return <label  key={v} className="checkbox-inline no_indent">
                    <input type="checkbox" value={v} defaultChecked={selected}
                           onClick={self.handleCheckboxTick.bind(self, v)}/> {v}
                </label>;
            }

            if (toDisplay.length == 0) return <span/>;

            var next = <X key={level} groups={filtered} level={level + 1} groupId={self.props.groupId}/>;
            return (
                <span>
                    <div className={"row row-level-"+level}>
                        {toDisplay.map(toRow)}
                    </div>
                    {next}
                </span>
            );
        }

    });


    return X;

});