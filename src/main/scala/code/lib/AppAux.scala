package code.lib

import zio.ZIO

object AppAux {

  val gh_token_S = System.getenv("GH_TOKEN")
  val gh_token =
    if (gh_token_S != null) {
      ZIO.debug("OAUTH token set from GH_TOKEN environment variable")
      gh_token_S
    } else {
      ZIO.debug("No GH_TOKEN environment variable found")
      null
    }

}
