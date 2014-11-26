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

define(['react', 'coreMixin', 'streamMixin', 'app_content_editor_mixin'], function (React, coreMixin, streamMixin, editorMixin) {

    return React.createClass({
        mixins: [coreMixin, streamMixin, editorMixin],

        schema: function () {
            return {
                "$schema": "http://json-schema.org/draft-04/schema#",
                "type": "object",
                "title": "Datasource configuration",
                "properties": {
                    "name": {
                        "propertyOrder": 10,
                        "title": "Name",
                        "type": "string"
                    },
                    "desc": {
                        "propertyOrder": 20,
                        "title": "Description",
                        "type": "string"
                    },
                    "sourceId": {
                        "propertyOrder": 30,
                        "title": "Value of 'sourceId' field",
                        "type": "string"
                    },
                    "tags": {
                        "propertyOrder": 40,
                        "title": "List of tags",
                        "type": "string"
                    },
                    "initialState": {
                        "propertyOrder": 50,
                        "title": "Initial state",
                        "type": "string",
                        "enum": ["Stopped", "Started"]

                    },
                    "source": {
                        "propertyOrder": 60,
                        "title": "Source configuration",
                        "type": "object",
                        "oneOf": [
                            {"$ref": "#/definitions/sourceFile", "title": "Log files"}
                        ]
                    },
                    "sink": {
                        "propertyOrder": 70,
                        "title": "Sink configuration",
                        "type": "object",
                        "oneOf": [
                            {"$ref": "#/definitions/sinkAkka", "title": "Akka endpoint"}
                        ]
                    }


                },
                "additionalProperties": false,
                "required": ["name", "initialState", "source", "sink"],
                "definitions": {
                    "sourceFile": {
                        "type": "object",
                        "properties": {
                            "class": {
                                "propertyOrder": 10,
                                "title": "Type",
                                "type": "string",
                                "template": "file"
                            },
                            "directory": {
                                "propertyOrder": 20,
                                "title": "Directory",
                                "type": "string"
                            },
                            "mainPattern": {
                                "propertyOrder": 30,
                                "title": "Current filename pattern",
                                "type": "string"
                            },
                            "rollingPattern": {
                                "propertyOrder": 40,
                                "title": "Rolled filename pattern",
                                "type": "string"
                            }
                        },
                        "additionalProperties": false
                    },
                    "sinkAkka": {
                        "type": "object",
                        "properties": {
                            "class": {
                                "propertyOrder": 10,
                                "title": "Type",
                                "type": "string",
                                "template": "akka"
                            },
                            "url": {
                                "propertyOrder": 20,
                                "title": "URL",
                                "type": "string"
                            }
                        },
                        "additionalProperties": false
                    }
                }
            }

                ;

        }

    });

});