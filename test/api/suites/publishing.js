"use strict";

var page = {
  "title": {
    "se": "Publishing page"
  },
  "slug": {
    "se": "publishing-page"
  },
  "widgets_ids": [1, 2]
};

module.exports = {
  suite: {
    name: "publishing",
    tests: [
      {
        name: "published version of page and its widgets",
        api_calls: [
          {
            it: "can create a page document with two widgets - one published and one not",
            request: "POST /pages",
            params: {data: {attributes: page}},
            save: {
              "page_id": "body.data.attributes.id"
            }
          },
          {
            it: "can get the created page",
            request: "GET /pages/{{page_id}}?relationships=1",
            assert: {
              select: "body.data.relationships.widgets.data.attributes.id",
              equal: [1, 2]
            }
          },
          {
            it: "cannot get published version of page as it's not published yet",
            request: "GET /pages/{{page_id}}?relationships=1&published=1",
            status: 404
          },
          {
            it: "can publish the page by setting published_version",
            request: "PUT /pages/{{page_id}}",
            params: {data: {attributes: {published_version: 1}}}
          },
          {
            it: "can now get the published version of the page with one published widget",
            request: "GET /pages/{{page_id}}?relationships=1&published=1",
            assert: {
              select: "body.data.relationships.widgets.data.attributes.id",
              equal: [1]
            }
          },
        ]
      }
    ]
  }
};
