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

define(['react', 'core_mixin'], function (React, core_mixin) {

    return React.createClass({
        mixins: [core_mixin],

        componentName: function () {
            return "app/content/notif/Content";
        },

        render: function () {
            return (
                <div className="row">
                    <div className="col-md-2">
                        <ul className="nav nav-pills dark nav-stacked">
                            <li role="presentation" className="active">
                                <a href="#">Home</a>
                            </li>
                            <li role="presentation">
                                <a href="#">Profile</a>
                            </li>
                            <li role="presentation">
                                <a href="#">Messages</a>
                            </li>
                        </ul>
                    </div>
                    <div className="col-md-10">
                        <table className="table">
                            <caption>Optional table caption.</caption>
                            <thead>
                                <tr>
                                    <th>#</th>
                                    <th>First Name</th>
                                    <th>Last Name</th>
                                    <th>Username</th>
                                </tr>
                            </thead>
                            <tbody>
                                <tr>
                                    <td>1</td>
                                    <td>Mark</td>
                                    <td>Otto</td>
                                    <td>@mdo</td>
                                </tr>
                                <tr>
                                    <td>2</td>
                                    <td>Jacob</td>
                                    <td>Thornton</td>
                                    <td>@fat</td>
                                </tr>
                                <tr>
                                    <td>3</td>
                                    <td>Larry</td>
                                    <td>the Bird</td>
                                    <td>@twitter</td>
                                </tr>
                            </tbody>
                        </table>
                    </div>

                </div>
            );
        }
    });

});