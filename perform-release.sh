GPG_TTY=$(tty)
export GPG_TTY
mvn release:perform -P sign -s settings.xml
