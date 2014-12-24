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

define(['react', 'core_mixin', './Subscriber'], function (React, core_mixin, Subscriber) {

    // use this.sendCommand(subject, data) to talk to server

    return React.createClass({

        mixins: [core_mixin],

        componentName: function () {
            return "plugin/desktopnotifications/SubscribeButton/" + this.props.ckey;
        },

        getInitialState: function () {
            return {connected: false, subscribed: false}
        },

        handleClick: function (e) {
            this.setState({subscribed: !this.state.subscribed});
        },

        render: function () {
            var self = this;

            var buttonClasses = this.cx({
                'disabled': (!self.state.connected),
                'btn btn-xs': true,
                'btn-default': self.state.subscribed,
                'btn-success': !self.state.subscribed
            });

            var button;
            if (this.state.subscribed)
                button = <button type="button" ref="button" className={buttonClasses} onClick={this.handleClick}>Unsubscribe</button>;
            else
                button = <button type="button" ref="button" className={buttonClasses} onClick={this.handleClick}>Subscribe</button>;
            return (
                <span>
                {button}
                    <Subscriber {...self.props} subscribed={self.state.subscribed} />
                </span>
            );
        }
    });

});