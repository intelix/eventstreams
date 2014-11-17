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
define(['react', 'coreMixin', 'admin/agent/List', 'admin/agent/datatap/ListContainer', 'admin/agent/datatap/AddNewForm'],
    function (React, coreMixin, List, TapListContainer, AddNew) {

    return React.createClass({
        mixins: [coreMixin],

        getInitialState: function () {
            return { selection: false }
        },

        handleSelection: function(evt) {
            this.setState({selection: evt.detail.id});
        },
        handleTapUnavailable: function(evt) {
            if (this.state.selection == evt.detail.id) {
                this.setState({selection: false});
            }
        },

        onMount: function() {
            this.addEventListener("tapSelected", this.handleSelection);
            this.addEventListener("tapUnavailable", this.handleTapUnavailable);
        },
        onUnmount: function() {
            this.removeEventListener("tapSelected", this.handleSelection);
            this.removeEventListener("tapUnavailable", this.handleTapUnavailable);
        },

        render: function () {

            var selection = this.state.selection
                ? <div><h3>Selected:</h3><TapListContainer addr={this.props.addr} id={this.state.selection} />
                    <div><AddNew {...this.props}  id={this.state.selection} /></div>
                    </div>
                : <div>Please select agent</div>

            return (
                <div>
                    <List  {...this.props} />
                    {selection}
                </div>
            )
        }
    });

});