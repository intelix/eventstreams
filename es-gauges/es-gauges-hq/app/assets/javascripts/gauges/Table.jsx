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

define(['packery', 'react', 'core_mixin', './GroupSelectors', './GaugeBar'], function (Packery, React, core_mixin, GroupSelectors, GaugeBar) {

    return React.createClass({
        mixins: [core_mixin],

        componentName: function () {
            return "app/content/gauges/Table";
        },

        getInitialState: function () {
            return {
                dict: {},
                sets: {},
                meta: {},
                selectedGroups: {},
                selectedDisplayOptions: ["c"],
                selectedWarningLevel: "0"
            }
        },

        subscribeToEvents: function () {
            return [
                ["gauges_group_select", this.groupSelect],
                ["gauges_group_unselect", this.groupUnselect]
            ]
        },

        groupSelect: function (evt) {
            var id = evt.detail.id;
            var g = this.state.selectedGroups;
            var sg = g[id];
            if (!sg) {
                sg = [];
                g[id] = [];
            }
            var idx = evt.detail.idx;
            var v = evt.detail.v;
            var c = sg[idx] ? sg[idx] : [];
            if ($.inArray(v, c) < 0) {
                c.push(v);
                g[id][idx] = c;
                this.setState({selectedGroups: g});
            }
        },
        groupUnselect: function (evt) {
            var id = evt.detail.id;
            var g = this.state.selectedGroups;
            var sg = g[id];
            if (!sg) {
                sg = [];
                g[id] = [];
            }
            var idx = evt.detail.idx;
            var v = evt.detail.v;
            var c = sg[idx] ? sg[idx] : [];
            c = c.filter(function (x) {
                return x != v;
            });
            g[id][idx] = c;
            this.setState({selectedGroups: g});
        },

        subscriptionConfig: function (props) {
            return [
                {address: props.addr, route: 'gauges', topic: 'dict', onData: this.onDict},
                {address: props.addr, route: 'gauges', topic: 'meta', onData: this.onMeta},
                {address: props.addr, route: 'gauges', topic: 'sets', onData: this.onSets}
            ];
        },
        remap: function (data) {
            var map = {};
            data.forEach(function (x) {
                map[x.id] = x;
            });
            return map;
        },

        onDict: function (data) {
            this.setState({dict: this.remap(data)});
        },
        onMeta: function (data) {
            this.setState({meta: this.remap(data)});
        },
        onSets: function (data) {
            this.setState({sets: data});
        },

        pckryLayout: function() {
            var container = this.refs.tCont.getDOMNode();

            this.pckry = new Packery(container, {
                // options
                itemSelector: '.tItem',
                gutter: 30
            });
        },

        componentDidMount: function () {
            this.pckryLayout();
        },

        componentDidUpdate: function () {
            this.pckryLayout();
        },

        handleDisplayOptionTick: function (v) {
            var selected = this.state.selectedDisplayOptions;
            if ($.inArray(v, selected) > -1) {
                selected = selected.filter(function (next) {
                    return next != v;
                });
            } else {
                selected.push(v);
            }
            this.setState({selectedDisplayOptions: selected});
        },

        handleWarningOptionTick: function (v) {
            this.setState({selectedWarningLevel: v});
        },

        renderData: function () {
            var self = this;
            var props = self.props;


            var displayOptions = [
                ["c", "Current"],
                ["a", "Mean"],
                ["50", "Median"],
                ["95", "95th"],
                ["98", "98th"],
                ["1m", "1m trend"],
                ["15m", "15m trend"],
                ["3h", "3h trend"],
                ["24h", "24h trend"],
                ["1w", "1w trend"]
            ];

            var warningOptions = [
                ["0", "All"],
                ["1", "Yellow and Red"],
                ["2", "Red"]
            ];

            function displayValueToDescription(v) {
                for (i of displayOptions) {
                    if (i[0] == v) return i[1];
                }
                return "?";
            }

            // TODO
            var metricLevelsYellow = [3,9,10,11,25];
            var metricLevelsRed = [4,8,30,29];
            var metrics = [
                {
                    id: 1,
                    name: "g:Applications.IDD.Benchmarks~c:Components.CaplinAdapter~m:QueueSize"
                },
                {
                    id: 2,
                    p: "C",
                    name: "g:Applications.IDD.Benchmarks~c:Components.CaplinAdapter~m:Load"
                },
                {
                    id: 3,
                    name: "g:Applications.IDD.XL~c:Actors.Actor1~m:QueueSize"
                },
                {
                    id: 4,
                    p: "B",
                    name: "g:Applications.IDD.XL~c:Actors.Actor1~m:Requests/sec/95th"
                },
                {
                    id: 5,
                    p: "A",
                    name: "g:Applications.IDD.XL~c:Actors.Actor1~m:AvgLatency"
                },
                {
                    id: 6,
                    name: "g:Applications.IDD.XL~c:Actors.ActorX~m:QueueSize"
                },
                {
                    id: 7,
                    p: "B",
                    name: "g:Applications.IDD.XL~c:Actors.ActorX~m:Requests/sec/95th"
                },
                {
                    id: 8,
                    p: "A",
                    name: "g:Applications.IDD.XL~c:Actors.ActorX~m:AvgLatency"
                },
                {
                    id: 9,
                    name: "g:Applications.IDD.Benchmarks~c:Actors.Actor1~m:QueueSize"
                },
                {
                    id: 10,
                    p: "B",
                    name: "g:Applications.IDD.Benchmarks~c:Actors.Actor1~m:Requests/sec/95th"
                },
                {
                    id: 11,
                    p: "A",
                    name: "g:Applications.IDD.Benchmarks~c:Actors.Actor1~m:AvgLatency"
                },
                {
                    id: 12,
                    name: "g:Applications.IDD.Benchmarks~c:Actors.Actor2~m:QueueSize"
                },
                {
                    id: 13,
                    p: "B",
                    name: "g:Applications.IDD.Benchmarks~c:Actors.Actor2~m:Requests/sec/95th"
                },
                {
                    id: 14,
                    p: "A",
                    name: "g:Applications.IDD.Benchmarks~c:Actors.Actor2~m:AvgLatency"
                },
                {
                    id: 15,
                    name: "g:Applications.IDD.Benchmarks~c:Actors.Actor3~m:QueueSize"
                },
                {
                    id: 16,
                    p: "B",
                    name: "g:Applications.IDD.Benchmarks~c:Actors.Actor3~m:Requests/sec/95th"
                },
                {
                    id: 17,
                    p: "A",
                    name: "g:Applications.IDD.Benchmarks~c:Actors.Actor3~m:AvgLatency"
                },
                {
                    id: 181,
                    p: "A",
                    name: "g:Resources.Hardware~c:CPU.All~m:Busy%"
                },
                {
                    id: 18,
                    p: "C",
                    name: "g:Resources.Hardware~c:CPU.All~m:User%"
                },
                {
                    id: 19,
                    p: "C",
                    name: "g:Resources.Hardware~c:CPU.All~m:System%"
                },
                {
                    id: 20,
                    name: "g:Resources.Hardware~c:CPU.All~m:Idle%"
                },
                {
                    id: 21,
                    name: "g:Resources.Hardware~c:CPU.CPU1~m:User%"
                },
                {
                    id: 22,
                    name: "g:Resources.Hardware~c:CPU.CPU1~m:System%"
                },
                {
                    id: 23,
                    name: "g:Resources.Hardware~c:CPU.CPU1~m:Idle%"
                },
                {
                    id: 24,
                    name: "g:Resources.Hardware~c:CPU.CPU2~m:User%"
                },
                {
                    id: 25,
                    name: "g:Resources.Hardware~c:CPU.CPU2~m:System%"
                },
                {
                    id: 26,
                    name: "g:Resources.Hardware~c:CPU.CPU2~m:Idle%"
                },
                {
                    id: 27,
                    name: "g:Resources.Hardware~c:CPU.CPU3~m:User%"
                },
                {
                    id: 28,
                    name: "g:Resources.Hardware~c:CPU.CPU3~m:System%"
                },
                {
                    id: 29,
                    name: "g:Resources.Hardware~c:CPU.CPU3~m:Idle%"
                },
                {
                    id: 30,
                    p: "Z",
                    name: "g:Applications.ID~m:Uptime%"
                }
            ];

            // TODO remove!!!!  metrics should arrive already sorted!!! Or sort once on arrival
            metrics = metrics.sort(function(a,b) {
                if (!a.p && !b.p) return 0;
                var ap = a.p ? a.p : "M";
                var bp = b.p ? b.p : "M";
                if (ap < bp) {
                    return -1;
                } else {
                    return 1;
                }
            });

            var segments = [
                ["h", "Location"],
                ["g", "Group"],
                ["c", "Component"],
                ["m", "Metric"]
            ];


            function parseMetrics(list) {

                function buildName(arr) {
                    return arr
                        .filter(function (next) {
                            return next && next.length > 0;
                        })
                        .map(function (next) {
                            return next.join(".");
                        }).join(" / ");
                }

                return list.map(function (next) {
                    if (!next.id) return next;
                    var unnamedCounter = 0;
                    var obj = {
                        name: next.name,
                        id: next.id
                    };
                    next.name.split("~").forEach(function (nextSegment) {
                        var segmentNameId;
                        var segmentContents = [];
                        if (nextSegment && nextSegment.length > 2) {
                            if (nextSegment.indexOf(":") == 1) {
                                segmentNameId = nextSegment.substring(0, 1);
                                segmentContents = nextSegment.substring(2).split(".");
                            } else {
                                segmentContents = nextSegment.split(".")
                            }
                        }
                        if (!segmentNameId) {
                            segmentNameId = "" + (++unnamedCounter)
                        }
                        obj[segmentNameId] = segmentContents;
                    });

                    var comp = obj["c"];
                    var subgroup = [];
                    if (comp && comp.length > 1) {
                        subgroup = [comp[0]];
                        comp = comp.slice(1);
                    }

                    obj.groupName = buildName([obj["h"], obj["g"], subgroup]);
                    obj.componentName = buildName([comp]);
                    obj.metricName = buildName([obj["m"]]);
                    segments.forEach(function (nextRequiredSegment) {
                        if (!obj[nextRequiredSegment[0]]) obj[nextRequiredSegment[0]] = ["Unspecified"];
                    });
                    return obj;
                });
            }

            var parsedMetrics = parseMetrics(metrics);

            var selectedGroups = self.state.selectedGroups;


            function narrowFrom(list, groupId, byId, atlevel) {
                var filtered = list.filter(function (next) {
                    var seg = next[groupId];
                    return (seg && seg.length > atlevel && seg[atlevel] == byId);
                });
                if (filtered.length == 0) return [];
                return selectFrom(filtered, groupId, atlevel + 1);
            }

            function hasSelectionAt(list, groupId, atlevel) {
                return selectedGroups[groupId] && selectedGroups[groupId][atlevel] && selectedGroups[groupId][atlevel].length > 0;
            }

            function selectFrom(list, groupId, atlevel) {
                if (!hasSelectionAt(list, groupId, atlevel)) return list;
                var combined = [];
                var refinedGroups = [];
                selectedGroups[groupId][atlevel].forEach(function (next) {
                    var filtered = narrowFrom(list, groupId, next, atlevel);
                    filtered.forEach(function (x) {
                        if ($.inArray(x, combined) < 0) combined.push(x);
                    });
                });
                if (combined.length == 0)
                    return list;
                else
                    return combined;
            }


            var selectedMetrics = parsedMetrics;
            var segmentsHtml = [];


            //var hadSelection = true;
            segments.forEach(function (next) {
                var k = next[0];
                var v = next[1];
                //if (hadSelection ) {
                segmentsHtml.push(
                    <div key={k}>
                        <h4>{v}</h4>

                        <div className="checkbox-block">
                            <GroupSelectors groups={selectedMetrics} level={0} groupId={k}/>
                        </div>
                    </div>
                );

                //hadSelection = hasSelectionAt(selectedMetrics, k, 0);
                selectedMetrics = selectFrom(selectedMetrics, k, 0);
                //self.logWarn("!>>>> selection at " + k + " = " + hadSelection);
                //}
            });

            var selectedDisplayOptions = self.state.selectedDisplayOptions;
            selectedDisplayOptions = selectedDisplayOptions && selectedDisplayOptions.length > 0 ? selectedDisplayOptions : [];
            var selectedWarningLevel = self.state.selectedWarningLevel;


            function isInYellow(id) {
                return $.inArray(id, metricLevelsYellow) > -1;
            }
            function isInRed(id) {
                return $.inArray(id, metricLevelsRed) > -1;
            }
            function filterByWarningLevel(metrics) {
                return metrics.filter(function(next) {
                    return (selectedWarningLevel == 0
                        || (selectedWarningLevel == 1 && (isInYellow(next.id) || isInRed(next.id)) )
                        || (selectedWarningLevel == 2 && isInRed(next.id) )
                    );
                });
            }

            selectedMetrics = filterByWarningLevel(selectedMetrics);



            var requiredOverlappingFactor = 0.6;

            function regroup(flatList) {

                var tables = [];

                function columns(metrics) {
                    var selectedColumns = [];
                    var selectedColumnsHash = [];
                    metrics.forEach(function (i) {
                        var v = i.metricName;
                        if ($.inArray(v, selectedColumnsHash) < 0) {
                            selectedColumnsHash.push(v);
                            selectedDisplayOptions.forEach(function (x) {
                                var fullName = v + " @ " + displayValueToDescription(x);
                                selectedColumns.push({mId: v, columnName: fullName, displayValue: x});
                            });
                        }
                    });
                    return selectedColumns;
                }

                function calculateFactor(table, columns) {
                    function toName(x) {
                        return x.columnName;
                    }

                    var a;
                    var b;
                    var columnsInTable = table.columns.length;
                    if (columnsInTable < columns.length) {
                        a = table.columns;
                        b = columns;
                    } else {
                        b = table.columns;
                        a = columns;
                    }
                    if (a.length == 0) return 0;
                    var overlappingCounter = 0;
                    var aColumns = a.map(toName);
                    var bColumns = b.map(toName);
                    aColumns.forEach(function (nextC) {
                        if ($.inArray(nextC, bColumns) > -1) overlappingCounter++;
                    });
                    return (overlappingCounter / aColumns.length);
                }

                function findTableFor(columns) {
                    var bestFit = false;
                    tables.forEach(function (nextT) {
                        var factor = calculateFactor(nextT, columns);
                        if (factor >= requiredOverlappingFactor) bestFit = nextT;
                    });
                    if (!bestFit) {
                        bestFit = {columns: [], id: tables.length};
                        tables.push(bestFit);
                    }
                    return bestFit;
                }

                function addTo(targetStructure, metricToAdd) {
                    var g = metricToAdd.groupName;

                    var groupContainer = targetStructure[g];
                    if (!groupContainer) {
                        groupContainer = {keys: []};
                        groupContainer.id = g;
                        targetStructure[g] = groupContainer;
                        if (!targetStructure.keys) targetStructure.keys = [];
                        targetStructure.keys.push(g);
                    }

                    var v = metricToAdd.componentName;

                    var componentContainer = groupContainer[v];
                    if (!componentContainer) {
                        componentContainer = {};
                        componentContainer.metrics = [];
                        componentContainer.id = v;
                        groupContainer.keys.push(v);
                        groupContainer[v] = componentContainer;
                    }

                    componentContainer.metrics.push(metricToAdd);
                }

                var metricsByGroupByBucket = {keys: []};
                flatList.forEach(function (i) {
                    addTo(metricsByGroupByBucket, i);
                });

                metricsByGroupByBucket.keys.forEach(function (groupKey) {
                    var group = metricsByGroupByBucket[groupKey];
                    group.keys.forEach(function (componentKey) {
                        var component = group[componentKey];
                        var componentColumns = columns(component.metrics);
                        var table = findTableFor(componentColumns);
                        component.metrics.forEach(function (i) {
                            addTo(table, i);
                        });
                        componentColumns.forEach(function (nextC) {
                            if (!table.columns.some(function (c) {
                                    return c.columnName == nextC.columnName;
                                })) {
                                table.columns.push(nextC);
                            }
                        });
                    });
                });


                return tables;
            }


            var tables = regroup(selectedMetrics);

            function metricFor(bucket, mId) {
                if (!bucket || !bucket.metrics || bucket.metrics.length < 1) return false;
                for (next of bucket.metrics) {
                    if (next.metricName && next.metricName == mId) return next;
                }
                return false;
            }


            function toTable(table) {
                function toTableRow(key) {
                    var group = table[key];
                    return group.keys.map(function (bucketKey) {
                        var bucket = group[bucketKey];
                        return (
                            <tr  key={group.id + ":" + bucket.id}>
                                <td className="gauge"><h6>{bucket.id}</h6></td>

                                {table.columns.map(function (x) {
                                    var content = "";
                                    var metric = metricFor(bucket, x.mId);
                                    if (metric) {
                                        content = <GaugeBar metric={metric} displayValue={x.displayValue} isYellow={isInYellow(metric.id)} isRed={isInRed(metric.id)} />;
                                    }
                                    return (
                                        <td key={metric.id + ":" + x.displayValue} className="gauge" valign="middle">
                                            <h6>{content}</h6>
                                        </td>
                                    );

                                })}
                            </tr>
                        );

                    });
                }

                function toTableGroupRow(key) {
                    var group = table[key];
                    return (
                        <tr key={group.id}>
                            <td colSpan="100%" className="gauge instrument-group"><h6>{group.id}</h6></td>
                        </tr>
                    );
                }

                function toTableGroup(key) {
                    return [toTableGroupRow(key), toTableRow(key)];
                }


                var tableVDOM = "";

                var selectedColumns = table.columns;

                if (selectedColumns && selectedColumns.length > 0) {
                    tableVDOM = (
                        <div key={table.id} className="tItem">
                            <table className="table table-condensed table-header-rotated compact">
                                <thead>
                                <tr>
                                    <th></th>
                                    {selectedColumns.map(function (i) {
                                        return (
                                            <th key={i.columnName} className="rotate-45">
                                                <div><span><h6>{i.columnName}</h6></span></div>
                                            </th>
                                        );
                                    })}
                                </tr>
                                </thead>
                                <tbody>
                                {table.keys.map(toTableGroup)}
                                </tbody>
                            </table>
                        </div>
                    );
                }


                return tableVDOM;
            }



            return (
                <div>
                    <div className="container-fluid">
                        <div className="row">
                            <div className="col-md-8">
                                {segmentsHtml}
                            </div>
                            <div className="col-md-4">
                                <h4>Warning level</h4>

                                <div className="checkbox-block">
                                    {warningOptions.map(function (next) {
                                        return (
                                            <label key={next[0]} className="radio-inline no_indent">
                                                <input type="radio" name="wo"
                                                       defaultChecked={next[0] == selectedWarningLevel}
                                                       onClick={self.handleWarningOptionTick.bind(self, next[0])}/> {next[1]}
                                            </label>
                                        );
                                    })}
                                </div>

                                <h4>Display option</h4>

                                <div className="checkbox-block">
                                    {displayOptions.map(function (next) {
                                        return (
                                            <label key={next[0]} className="checkbox-inline no_indent">
                                                <input type="checkbox" name="do1"
                                                       defaultChecked={$.inArray(next[0], selectedDisplayOptions)> -1}
                                                       onClick={self.handleDisplayOptionTick.bind(self, next[0])}/> {next[1]}
                                            </label>
                                        );
                                    })}
                                </div>


                            </div>
                        </div>
                    </div>

                    <div ref="tCont">
                        {tables.map(toTable)}
                    </div>

                </div>
            );
        },
        renderLoading: function () {
            return (
                <div>loading...</div>
            );
        },

        render: function () {
            if (this.state.dict) {
                return this.renderData();
            } else {
                return this.renderLoading();
            }
        }
    });

});