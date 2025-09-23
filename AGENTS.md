Backend application code:

* We always use the latest version of Spring boot, so use new features like the
  RestClient instead of RestTemplate and JDBCClient instead of JdbcTemplate
* Use the latest features of Kotlin if it makes sense
* do not check or write tests.
* Do not use comments, the code should be self-explanatory, prefer longer and meaningful names instead of comments
* Never split into Impl and interface, just assume there is one implementation
* Thymeleaf for server-side rendering. 
Use URL expression resolution with @{} instead of string concatenation to build urls
* Hibernate and JPA for ORM, be careful to avoid N+1 and other database performance issues

Backend database:

* Flyway for migrations, database updates
* Latest Postgres 17: Use newer and more modern features and avoid bad practices.
GENERATED ALWAYS AS IDENTITY PRIMARY KEY, for primary keys 
Be careful to not use bigint if the table does not obviously grow extremely large
typically integer or smallint will suffice 
* Generate DDL so that it optimizes storage space (column tetris)

Frontend:

* We use the new web component framework webawesome.com
  You can search web-types.json to make sure you use
  the correct API, WA also have utility classes, which are all prefixed by wa-, the
  variables for colors, text etc. are also all prefixed by wa-
* The only libraries we use: 
Web awesome, HTMX, htmx-ext-response-targets
No other libraries are in use or should be used
* All CSS classes start with either: wa-, r- or htmx-
* Prefer inline styling if styles will not realistically be reused
* Prefix our own CSS classes with r-
* Use modern CSS features, no need to care about older browser support, using polyfills if necessary
  to support older browsers. That means avoid vendor prefixes and only use standard CSS
Use the modern Temporal API, if time,zone, calendar functionality is needed
* Also use modern JS, for example use Temporal instead of new Date
And look up temporal API before using it: https://tc39.es/proposal-temporal/docs/cookbook.html
* Try to keep a minimum of frontend state.
Only use JS when it is absolutely needed for user experience
Prefer to use HTML fragments and get HTML from backend when possible. 
Thymeleaf supports fragments and can be used to replace part of the page with new data
* Avoid to manually make fetch requests, and instead use hx-get, hx-post, hx-delete
with hx-include selector that sends the relevant data to the server
* If you're not sure about HTMX API, check: https://htmx.org/reference/
* For spacing, colors, focus, shadows, typography. Use design tokens: https://webawesome.com/docs/tokens/
* For layout, use layout classes provided here: https://webawesome.com/docs/layout/ 
before creating our own custom layout classes. 
* Before creating custom CSS, see if we can have the same effect from a component: https://webawesome.com/docs/components/
For example wa-card can replace the need for custom "container" classes