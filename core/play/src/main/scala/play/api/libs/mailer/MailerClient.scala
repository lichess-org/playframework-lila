package play.api.libs.mailer

import scala.collection.JavaConverters._

trait MailerClient {

  /**
   * Sends an email with the provided data.
   *
   * @param data data to send
   * @return the message id
   */
  def send(data: Email): String
}
