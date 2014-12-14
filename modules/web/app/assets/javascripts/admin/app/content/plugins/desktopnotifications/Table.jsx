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

define(['react', 'core_mixin', './TableRow', './TableRowSelected'],
    function (React, core_mixin, Row, RowSelected) {

    return React.createClass({
        mixins: [core_mixin],

        componentName: function () {
            return "plugin/desktopnotifications/Table";
        },

        subscriptionConfig: function (props) {
            return [{address: props.addr, route: 'signalsub', topic: 'list', dataKey: 'list'}];
        },
        getInitialState: function () {
            return {
                list: null,
                selectedItems: []
            }
        },

        subscribeToEvents: function () {
            return [
                ["signalsubTableRowClicked", this.handleTableRowClick]
            ]
        },

        handleTableRowClick: function (evt) {
            var self = this;
            var currentlySelected = self.state.selectedItems;
            var newSelection = evt.detail.ckey;
            if ($.inArray(newSelection, currentlySelected) > -1) {
                currentlySelected = currentlySelected.filter(function(e) { return e != newSelection});
            } else {
                currentlySelected.push(newSelection)
            }
            this.setState({selectedItems: currentlySelected});
        },

        handleAddNew: function () {
            this.raiseEvent("addSignalSubscription", {});
        },

        renderData: function () {
            var props = this.props;
            var self = this;

            var header = <tr>
                    <th>Name</th>
                    <th>Class</th>
                    <th>Subclass</th>
                    <th>Level</th>
                    <th>Conflate</th>
                    <th>Auto-close</th>
                    <th>State</th>
                    <th>Notifications</th>
                </tr>;


            function row(el) {

                var selected = $.inArray(el.ckey, self.state.selectedItems) > -1;

                var items = [<Row {...props} key={el.ckey} ckey={el.ckey} selected={selected} />];
                if (selected) items.push(<RowSelected {...props} key={"selected_"+el.ckey} ckey={el.ckey} />);
                return items;
            }

            var buttonClasses = this.cx({
                'disabled': (!self.state.connected)
            });

            var addButton = <div className="withspace">
                <button type="button" className={"btn btn-default btn-sm " + buttonClasses} onClick={self.handleAddNew} >
                    Add new
                </button>
            </div>;

            var tableContent = <span>
                {addButton}

                <table  className="table table-striped table-hover" >
                    <thead>
                        {header}
                    </thead>
                    <tbody >
                        {self.state.list.map(row)}
                    </tbody>
                </table>

            </span>;



            return (
                <div className="row">
                    <div className="col-md-12">
                    {tableContent}
                    </div>
                </div>
            );
        },
        renderLoading: function () {
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