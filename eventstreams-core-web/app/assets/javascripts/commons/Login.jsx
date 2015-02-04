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

define(['react', 'core_mixin', 'crypto_sha256'], function (React, core_mixin, Crypto) {

    return React.createClass({

        mixins: [core_mixin],

        componentName: function () {
            return "app/content/commons/Login";
        },

        getInitialState: function () {
            return {connected: false}
        },

        handleKill: function (e) {
            if (confirm("Are you sure?")) {
                this.sendCommand(this.props.addr, this.props.ckey, "reset", {});
            }
        },


        handleSubmit: function() {
            var remember = this.refs.formRemember.getDOMNode().checked;
            var user = this.refs.formUser.getDOMNode().value;
            var passw = CryptoJS.SHA256(this.refs.formPassword.getDOMNode().value).toString(CryptoJS.enc.Hex);

            this.sendCommand("local", ":auth", "auth_cred", {u:user, p:passw, r: remember});

        },
        
        render: function () {
            var self = this;

            //var x = this.readCookie('ppkcookie');
            //this.createCookie('ppkcookie','testcookie',7);
            
            var buttonClasses = this.cx({
                'disabled': (!self.state.connected),
                'btn btn-default btn-xs': true
            });

            return (
                <div className="container">
                    <form className="form-signin">
                        <h2 className="form-signin-heading text-muted">eventstreams HQ</h2>
                        <input type="test" id="inputEmail" className="form-control" placeholder="Username" required="true" autofocus="true" ref="formUser"/>
                        <input type="password" id="inputPassword" className="form-control" placeholder="Password" required="true" ref="formPassword"/>
                        <div className="checkbox">
                            <label>
                                <input type="checkbox" value="remember-me" ref="formRemember"/>
                                Remember me for a week
                            </label>
                        </div>
                        <button className="btn btn-lg btn-primary btn-block" type="button" onClick={this.handleSubmit}>Sign in</button>
                    </form>
                </div>
            );


        }
    });

});