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
define(['react', 'coreMixin', 'streamMixin'], function (React, coreMixin, streamMixin) {

    // use this.sendCommand(subject, data) to talk to server

    return React.createClass({

        mixins: [coreMixin, streamMixin],

        getInitialState: function () {
            return {connected: false}
        },

        onClick: function(e) {
            this.sendCommand(this.props.addr, this.props.route, "reset", {});
        },

        render: function () {

            var button =
                    <button type="button" className="btn btn-primary btn-sm" onClick={this.onClick}>
                    reset
                    </button>;

            return button;
        }
    });

});