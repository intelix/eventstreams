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

define(['react', 'core_mixin', './Table', './Editor'],
    function (React, core_mixin, Table, Editor) {

        return React.createClass({
            mixins: [core_mixin],

            componentName: function () {
                return "app/content/gates/sensors/main/" + this.props.ckey;
            },

            getInitialState: function () {
                return {}
            },

            subscribeToEvents: function() { return [
                ["addSensor", this.openModal],
                ["editSensor", this.openEditModal],
                ["sensorsModalClosed", this.closeModal]
            ]},

            openModal: function (evt) {
                var defaults = {
                    "name": "",
                    "initialState": "Active",
                    "simpleCondition": "",
                    "occurrenceCondition": "Exactly",
                    "occurrenceCount": 0,
                    "occurrenceWatchPeriodSec": 1,
                    "signalClass": "",
                    "signalSubclass": "",
                    "level": "Very low",
                    "title": "",
                    "body": "",
                    "icon": "",
                    "correlationIdTemplate": "",
                    "transactionMarking": "None",
                    "timestampSource": "date_ts"
                };
                this.setState({editBlock: <Editor addr={this.props.addr} addRoute={this.props.ckey + "/sensors"} title="Sensor configuration" defaults={defaults}  editorId="sensors"/>});
            },
            openEditModal: function (evt) {
                this.setState({editBlock: <Editor addr={this.props.addr} ckey={evt.detail.ckey} title="Sensor configuration"  editorId="sensors"/>});
            },
            closeModal: function () {
                this.setState({editBlock: false});
            },


            renderData: function () {
                var self = this;

                return (
                    <div className="row withspace">

                        <div className="col-md-12">
                            <Table {...self.props} />
                            {this.state.editBlock ? this.state.editBlock : ""}
                        </div>

                    </div>
                );

            },

            render: function () {
                return this.renderData();
            }
        });

    });