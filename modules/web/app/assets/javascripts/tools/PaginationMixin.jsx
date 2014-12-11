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

define(['react','logging'], function (React, logging) {

    return {
        renderPageLink: function (callback, text, active, withLink) {
            if (withLink) {
                return <li className={active ? "active" : ""}>
                    <a href="#" onClick={callback}>{text}</a>
                </li>;
            } else {
                return <li>
                    <span aria-hidden="true">{text}</span>
                </li>;
            }
        },

        handlePaginationClick: function (i) {
            var self = this;
            var state = self.state;
            var pages = ~~(state.list.length / state.pageSize);
            if (pages * state.pageSize < state.list.length) pages = pages + 1;

            var page = i;

            if (page < 1) page = 1;
            if (page > pages) page = pages;
            self.setState({page: page});
        },

        renderPagination: function () {
            var self = this;
            var state = self.state;

            if (!state.list || state.list.length < state.pageSize) return <span/>;
            var leftPointer, rightPointer, pagesLinks;

            var pages = ~~(state.list.length / state.pageSize);
            if (pages * state.pageSize < state.list.length) pages = pages + 1;

            var page = state.page;

            if (page < 1) page = 1;
            if (page > pages) page = pages;

            var start = page - 2;
            if (start < 1) start = 1;
            var finish = page + 2;
            if (finish > pages) finish = pages;


            var firstItem = start > 1 ? self.renderPageLink(self.handlePaginationClick.bind(self, 1), "1", page == 1, true) : "";
            var lastItem = finish < pages ? self.renderPageLink(self.handlePaginationClick.bind(self, pages), pages, page == pages, true) : "";

            var leftSeparator = start > 2 ? self.renderPageLink(function () {
            }, "...", false, false) : "";
            var rightSeparator = finish < pages - 1 ? self.renderPageLink(function () {
            }, "...", false, false) : "";

            if (start > finish) finish = start;

            var items = [];
            for (i = start; i <= finish; i++) {
                items.push(self.renderPageLink(self.handlePaginationClick.bind(self, i), i, i == page, true));
            }

            return <nav>
                <ul className="pagination pagination-sm">
                {firstItem}
                {leftSeparator}
                {items}
                {rightSeparator}
                {lastItem}
                </ul>
            </nav>;
        },

        itemsOnCurrentPage: function() {
            var self = this;
            var state = self.state;
            var pages = ~~(state.list.length / state.pageSize);
            if (pages * state.pageSize < state.list.length) pages = pages + 1;

            var page = state.page;

            if (page < 1) page = 1;
            if (page > pages) page = pages;

            var from = (page - 1) * state.pageSize;
            var to = from + state.pageSize;
            if (to > state.list.length) to = state.list.length;

            return state.list.slice(from,to);
        }


    };

});