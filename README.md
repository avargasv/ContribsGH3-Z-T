  <h2>REST API ContribsGH2-Z</h2>
      A REST service that given the name of a GitHub organization returns a list of contributors 
      (by organization or by repo) in JSON format, sorted by the number of contributions
      <br/>
      <br/>
      Parallel version using ZIO HTTP and a Redis cache
      <br/>

  <h2>How to use the service</h2>
      <ol>
        <li>
          Clone the repository
        </li>
        <li>
          Run sbt under the repository's home directory
        </li>
        <li>
          After the sbt prompt '>' appears, execute the following sbt command:
        <br/>
          run
        </li>
        <li>
          Call the rest service with the following url:
          <br/>
          http://localhost:8080/org/{org_name}/contributors?group-level={organization|repo}&min-contribs={integer_value}
        </li>
      </ol>
