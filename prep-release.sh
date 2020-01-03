GPG_TTY=$(tty)
export GPG_TTY
mvn release:prepare -P sign -s settings.xml
