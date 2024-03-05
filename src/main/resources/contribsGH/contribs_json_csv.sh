curl "https://api.github.com/repos/revelation/ey-cloud-recipes/contributors" | jq '[.[] | { repository: "ey-cloud-recipes", login: .login, contributions: .contributions }]' > contribs_ey-cloud-recipes.json

cat all-contribs.json | jq '.[] | [ .repository, .login, .contributions ] | @csv' > all-contribs.csv
