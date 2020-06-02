# API REST Gerenciamento de Sessões de Votação Sicredi 


## Microsserviço votacao-discovery-indexer

Para o requisito adicional de descoberta dos resultados das votações das pautas, a API REST de Gerenciamento de Sessões de Votação é limitada. 

Dessa forma, faz-se necessário a utilização de um serviço adicional para atuar como responsável pela realização desse tipo de descoberta. Nesse sentido, faz-se uso do serviço `CloudSearch` da Amazon.

Para alcançar o objetivo desse requisito, é necessário manter um índice dos resultados das votações cadastradas. Dessa forma, o `CloudSearch` é responsável por manter esse índice atualizado e por servir os resultados das buscas.

Para isso, o microsserviço `votacao` armazena cada novo resultado de votação em uma fila SQS de mensagens. O segundo microsserviço, `votacao-discovery-indexer`, é responsável por receber essas mensagens, adicionando os resultados das votações ao índice.

O microsserviço `votacao-discovery-indexer`, na verdade, consiste de dois sistemas integrados do `CloudSearch`: o indexador dos resultados das votações é responsável por popular os índices e o serviço de descoberta é responsável por servir os resultados das buscas.

Nesse sentido, é, também, empregado o design pattern conhecido como `CQRS (Command Query Responsibility Segregation)`, o qual provê vários benefícios, como permitir escalar duas partes (indexador e serviço de descoberta) de forma independente.

A integração com um microsserviço assíncrono, ou seja, `votacao-discovery-indexer`, é, também, mais resiliente. Se o indexador dos resultados das votações tiver uma interrupção, as mensagens são armazenadas na fila e o sistema é capaz de captá-las após recuperar-se.
