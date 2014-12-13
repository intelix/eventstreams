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

define(['react'],
    function (React) {

        return React.createClass({

            render: function () {
                switch (this.props.state) {
                    case "active":
                        return <span className="label label-success">{this.props.details}</span>;
                        break;
                    case "passive":
                        return <span className="label label-default">{this.props.details}</span>;
                        break;
                    case "replay":
                        return <span className="label label-info">{this.props.details}</span>;
                        break;
                    case "error":
                        return <span className="label label-danger">{this.props.details}</span>;
                        break;
                    case "unknown":
                        return <span className="label label-warning">{this.props.details}</span>;
                        break;
                    default:
                        return <span className="label label-warning">unknown - {this.props.state}</span>;
                        break;
                }
            }
        });

    });