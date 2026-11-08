import { Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { Modulo } from '../../modulos';

@Component({
  selector: 'app-em-construcao',
  templateUrl: './em-construcao.html',
  styleUrl: './em-construcao.css',
})
export class EmConstrucao {
  modulo = inject(ActivatedRoute).snapshot.data['modulo'] as Modulo;
}
