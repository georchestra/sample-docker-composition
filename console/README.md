# About

A simple docker composition to be able to run the [geOrchestra
console](https://github.com/georchestra/georchestra/tree/master/console) in
isolation.

No `security-proxy` nor `gateway` is being used in this composition, a `nginx`
webserver is being used as frontend webserver which hardcodes the http security
headers `sec-username` and `sec-roles`.

See the `datadir/nginx/nginx.conf` configuration file if you need to customize this.

# Usage

Once the composition has been launched, simply open [http://localhost:8080/console/](http://localhost:8080/console/)
in your browser.

