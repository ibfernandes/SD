- Ao compilar o arquivo graph.thrift você deve renomear os arquivos edge.java e vertex.java para Edge.java e Vertex.java
- As configurações de IP e PORTA do servidor/cliente podem ser feitas no arquivo: /Eclipse project/settings.ini
- Para rodar as demonstrações do programa vá até o arquivo /Eclipse project/src/server/Client.java
  e troque a variável CURRENT_STATE para um dos três modos de execução:
		- SHOW_CONCURRENCY: aloca apenas 2 vértices diferentes (id 0 e 1), travando em algum momento o uso do recurso
							para o outro cliente que estiver tentando fazer addEdge ou addVertex ao mesmo tempo.
		- USE_PRESET:		executa uma sequência de comandos específicos implementados no método preSetOperation()
		- USE_RANDOM:		executa TODAS as operações de forma aleatória.