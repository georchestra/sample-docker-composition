# About

This docker composition showcases how to integrate a statically generated
website with [Hugo](https://gohugo.io/).

# geonetwork-ui integration

Integrating your own search component requires only a couple of JavaScript lines, see below:

Using the following code snippet in your markdwon file:

```html
<script src="https://geonetwork.github.io/geonetwork-ui/main/demo/webcomponents/gn-wc.js"></script>

<gn-search-input
         api-url="/geonetwork/srv/api"
         open-on-search="/datahub/search/?q=${search}"
         open-on-select="/datahub/dataset/${uuid}"
         primary-color="#000"
         secondary-color="#fff"
         main-color="#555"
         background-color="#fdfbff"
         main-font="'Inter', sans-serif"
         title-font="'DM Serif Display', serif">
         </gn-search-input>

```
And you are done:

<script src="https://geonetwork.github.io/geonetwork-ui/main/demo/webcomponents/gn-wc.js"></script>

<gn-search-input
         api-url="/geonetwork/srv/api"
         open-on-search="/datahub/search/?q=${search}"
         open-on-select="/datahub/dataset/${uuid}"
         primary-color="#000"
         secondary-color="#fff"
         main-color="#555"
         background-color="#fdfbff"
         main-font="'Inter', sans-serif"
         title-font="'DM Serif Display', serif"></gn-search-input>
