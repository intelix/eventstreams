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

define(['react', 'core_mixin', './SidebarElementMixin'], function (React, core_mixin, SidebarElementMixin) {

    return React.createClass({
        mixins: [core_mixin, SidebarElementMixin],

        componentName: function() { return "app/navigation/Sidebar/" + this.props.name; },

        render: function () {
            return this.asSidebarElement(<span>{this.props.name}</span>);
        }
    });

});