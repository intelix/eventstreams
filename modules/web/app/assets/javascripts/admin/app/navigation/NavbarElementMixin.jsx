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

define(['react', 'coreMixin'], function (React, coreMixin) {

    return {

        getInitialState: function () {
            return {active: this.props.active}
        },

        onMount: function() {
            this.addEventListener("navBarSelection", this.handleSelectionEvent);
        },
        onUnmount: function() {
            this.removeEventListener("navBarSelection", this.handleSelectionEvent);
        },

        handleSelectionEvent: function(evt) {
            this.setState({active: false});
        },

        handleClick: function() {
            if (!this.state.active) {
                this.raiseEvent("navBarSelection", {id: this.props.id});
                this.setState({active: true});
            }
        },

        asNavbarElement: function (element) {
            var cx = React.addons.classSet;
            var classes = cx({
                'active': this.state.active
            });

            return <li className={classes}><a onClick={this.handleClick} href="#">{element}</a></li>;
        }
    };

});