import logging

try:
    import keyring
    import keyring.errors
except ImportError:
    keyring = None


class CredentialManager:
    SERVICE_NAME = "ClipCascade"

    @classmethod
    def is_available(cls) -> bool:
        return keyring is not None

    @classmethod
    def save_password(cls, username: str, password: str) -> bool:
        """
        Securely save the user password in the OS credential manager (Windows Credential Manager,
        macOS Keychain, or Linux Secret Service) using keyring.
        """
        if not username or not password:
            return False
        if not cls.is_available():
            logging.warning(
                "keyring module is not available. Cannot save password securely."
            )
            return False
        try:
            keyring.set_password(cls.SERVICE_NAME, username, password)
            logging.info(
                f"Securely saved password for user '{username}' in system credential store."
            )
            return True
        except Exception as e:
            logging.error(
                f"Failed to save password securely for user '{username}': {e}"
            )
            return False

    @classmethod
    def get_password(cls, username: str) -> str | None:
        """
        Retrieve the saved password from the OS credential manager.
        """
        if not username:
            return None
        if not cls.is_available():
            logging.warning(
                "keyring module is not available. Cannot retrieve saved password."
            )
            return None
        try:
            return keyring.get_password(cls.SERVICE_NAME, username)
        except Exception as e:
            logging.error(
                f"Failed to retrieve password for user '{username}': {e}"
            )
            return None

    @classmethod
    def delete_password(cls, username: str) -> bool:
        """
        Remove the stored password from the OS credential manager.
        """
        if not username or not cls.is_available():
            return False
        try:
            keyring.delete_password(cls.SERVICE_NAME, username)
            logging.info(
                f"Deleted stored password for user '{username}' from system credential store."
            )
            return True
        except Exception as e:
            logging.debug(
                f"Could not delete stored password for user '{username}': {e}"
            )
            return False
