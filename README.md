# Sistema de Lanchonete - Sistema de Gestão (MVP)

Sistema completo de gestão para lanchonetes de balcão/local, desenvolvido seguindo os princípios de **Clean Code**, **Clean Architecture** e **DRY**.

## 🚀 Stack Tecnológica

- **Backend**: Java 17 + Spring Boot 3.2+
- **Frontend**: Angular 17+ (Standalone Components)
- **Banco de Dados**: MySQL 8.0+
- **Desktop**: Electron
- **Containerização**: Docker Compose
- **Build Tool**: Maven 3.8+

## 📁 Estrutura do Projeto

```
sistema-lanchonete/
├── kernel-compartilhado/    # Kernel compartilhado (Value Objects, Exceptions, Utilitários)
├── gestao-pedidos/          # Gestão completa de pedidos (Lobby/Cozinha)
├── gestao-cardapio/        # Gestão de cardápio (produtos, categorias, itens)
├── gestao-clientes/         # Gestão de clientes (cadastro e histórico)
├── gestao-financeira/       # Gestão financeira (caixa, pagamentos, fechamento)
├── relatorios/              # Relatórios gerenciais (gráficos Chart.js)
├── notas-fiscais/           # Gestão de notas fiscais (emissão e controle)
├── sistema-orquestrador/    # Orquestrador principal (Spring Boot Application)
├── frontend/                # Frontend Angular 17+
├── docker/                  # Configurações Docker
├── Lobby-pedidos/           # Módulo legado (não será modificado)
└── Tela-principal/          # Será transformado no módulo `sistema-orquestrador`
```

## 📚 Documentação

- **[DOCUMENTACAO_ARQUITETURA_SISTEMA.md](./DOCUMENTACAO_ARQUITETURA_SISTEMA.md)**: Documentação completa da arquitetura
- **[DOCUMENTACAO_PLANO_DESENVOLVIMENTO_APP.md](./DOCUMENTACAO_PLANO_DESENVOLVIMENTO_APP.md)**: Plano de desenvolvimento
- **[GUIA_IMPLEMENTACAO_PRATICA.md](./GUIA_IMPLEMENTACAO_PRATICA.md)**: Guia prático com exemplos de código

## 🏗️ Arquitetura

O sistema utiliza **Maven Multi-Module** com **Clean Architecture**:

- **Domain Layer**: Entidades, Value Objects e regras de negócio puras
- **Application Layer**: Casos de uso, DTOs e interfaces (Ports)
- **Infrastructure Layer**: Implementações concretas (JPA, REST, etc.)

## 🎯 Módulos Funcionais

1. **Gestão de Pedidos (`gestao-pedidos`)**: Gestão completa da fila de pedidos (Lobby/Cozinha)
2. **Gestão de Cardápio (`gestao-cardapio`)**: CRUD de produtos, categorias e itens
3. **Gestão de Clientes (`gestao-clientes`)**: Cadastro e histórico de clientes
4. **Gestão Financeira (`gestao-financeira`)**: Controle de caixa, pagamentos (PIX, Cartão, Dinheiro) e fechamento
5. **Relatórios (`relatorios`)**: Visualizações com Chart.js
6. **Notas Fiscais (`notas-fiscais`)**: Emissão e gestão de NF

## 🛠️ Como Executar

### Pré-requisitos

- Java 17+
- Maven 3.8+
- Docker e Docker Compose
- Node.js 18+ (para frontend)

### Executando com Docker

```bash
# Subir containers (MySQL + Backend)
cd docker
docker-compose up -d

# Backend estará disponível em http://localhost:8080
```

### Executando Localmente

```bash
# Compilar todos os módulos
mvn clean install

# Executar aplicação
cd sistema-orquestrador
mvn spring-boot:run
```

## 📋 Status do Projeto

- [x] Documentação de arquitetura
- [x] Planejamento de módulos
- [ ] Estrutura base (POM raiz, kernel-compartilhado, sistema-orquestrador)
- [ ] Módulo Gestão de Cardápio
- [ ] Módulo Gestão de Pedidos
- [ ] Módulo Gestão de Clientes
- [ ] Módulo Gestão Financeira
- [ ] Módulo Relatórios
- [ ] Módulo Notas Fiscais
- [ ] Frontend Angular
- [ ] Integração Electron

## 🎓 Princípios Aplicados

- **Clean Code**: Código legível, simples e direto
- **Clean Architecture**: Separação estrita de responsabilidades
- **DRY**: Evitar duplicação de código
- **SOLID**: Princípios de design orientado a objetos
- **Domain-Driven Design**: Modelagem baseada no domínio

## 📝 Licença

Este projeto é privado e de uso interno.

---

**Desenvolvido com ❤️ seguindo as melhores práticas de engenharia de software**

