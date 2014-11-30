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

define(['react'], function (React) {

    // use this.sendCommand(subject, data) to talk to server

    return {


        getInitialState: function() {
            return { data: false};
        },

        subscriptionConfig: function (props) {
            return props.id ? [{address: props.addr, route: props.id, topic: 'props', onData: this.onData}] : [];
        },

        onData: function (data) {
            this.setState({data: data});
        },

        componentDidMount: function () {
            var self = this;
            if (!self.props.id) {
                var defaults = this.props.defaults || {};
                self.setState({data: defaults});
            }
        },

        componentDidUpdate: function() {
            var self = this;
            if (self.state.data) {
                $(self.refs.modal.getDOMNode()).on('hidden.bs.modal', function (e) {
                    self.raiseEvent("modalClosed", {});
                });
                $(self.refs.modal.getDOMNode()).modal({backdrop: false});

                self.editor = new JSONEditor(self.refs.editor.getDOMNode(), {
                    theme: 'bootstrap3',
                    schema: this.schema(),
                    disable_properties: true,
                    disable_collapse: false,
                    no_additional_properties: true
                });
                self.editor.setValue(self.state.data);
            }
        },

        handleAdd: function (e) {
            var self = this;
            var value = self.editor.getValue();
            if (self.props.addRoute) {
                self.sendCommand(self.props.addr, self.props.addRoute, "add", value);
                if (self.onConfigWillBeAdded) {
                    self.onConfigWillBeAdded(value);
                }
            }
            if (self.props.id) {
                self.sendCommand(self.props.addr, self.props.id, "update_props", value);
                if (self.onConfigWillBeUpdated) {
                    self.onConfigWillBeUpdated(value);
                }
            }

            $(self.refs.modal.getDOMNode()).modal('hide');
            return true;
        },

        render: function () {

            var self = this;

            function wrap(data) {
                return <div className="modal" ref="modal" tabIndex="-1" role="dialog" aria-labelledby="myModalLabel" aria-hidden="true">
                    <div className="modal-dialog modal-lg">
                        <div className="modal-content">
                            <div className="modal-header">
                                <button type="button" className="close" data-dismiss="modal">
                                    <span aria-hidden="true">&times;</span>
                                    <span className="sr-only">Close</span>
                                </button>
                                <h4 className="modal-title" id="myModalLabel">{self.props.title ? self.props.title : ""}</h4>
                            </div>
                            {data}
                        </div>
                    </div>
                </div>
            }

            var contents;
            if (self.state.data) {
                contents = <div ref="editor"/>;
            } else {
                contents = <div>Loading...</div>;
            }
            return wrap(
                <span>
                    <div className="modal-body">
                    {contents}
                    </div>
                    <div className="modal-footer">
                        <button type="button" className="btn btn-default" data-dismiss="modal">Close</button>
                        <button type="button" className="btn btn-primary" onClick={this.handleAdd}>Save changes</button>
                    </div>
                </span>
            );
        }
    };




});