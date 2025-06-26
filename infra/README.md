*** README ***

Mermaid test

The following code-block will be rendered as a Mermaid diagram:

```mermaid
flowchart LR
  A --> B
```

The following code-block will be rendered as a Mermaid graph:

```mermaid
graph TD
    A[Load Balancer] --> B[Web Server 1]
    A --> C[Web Server 2]
    B --> D[Database]
    C --> D
```
And bigger one

```mermaid
graph TD
    A[Enter Chart Definition] --> B(Preview)
    B --> C{decide}
    C --> D[Keep]
    C --> E[Edit Definition]
    E --> B
    D --> F[Save Image and Code]
    F --> B
```


