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


        handleAdd: function (e) {
            var name = this.refs.gateName.getDOMNode().value.trim();

            if (!name) return;

            this.sendCommand(this.props.addr, "gates", "add", {name: name});

            this.refs.gateName.getDOMNode().value = null;

            this.refs.modal.getDOMNode().modal('hide');

            return true;
        },


        onMount: function () {
            var self = this;
            $(this.refs.modal.getDOMNode()).on('hidden.bs.modal', function (e) {
                self.raiseEvent("modalClosed", {});
            });
            $(this.refs.modal.getDOMNode()).modal('show');
        },

        renderEditBlock: function (data) {

            var self = this;

            if (data) alert("ok");

            function wrap(data) {
                return <div className="modal fade" ref="modal" tabIndex="-1" role="dialog" aria-labelledby="myModalLabel" aria-hidden="true">
                    <div className="modal-dialog">
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

            if (data) {
                var value = JSON.stringify(data, null, 4);
                return wrap(
                    <span>
                        <div className="modal-body">
                            <textarea className="form-control" ref="gateName" placeholder="Gate name" defaultValue={value} rows="20"/>
                        </div>
                        <div className="modal-footer">
                            <button type="button" className="btn btn-default" data-dismiss="modal">Close</button>
                            <button type="button" className="btn btn-primary" onClick={this.handleAdd}>Save changes</button>
                        </div>
                    </span>
                );
            } else {
                return wrap(
                    <span>
                        <div className="modal-body">
                            Loading ...
                        </div>
                    </span>
                );
            }
        }
    };

});